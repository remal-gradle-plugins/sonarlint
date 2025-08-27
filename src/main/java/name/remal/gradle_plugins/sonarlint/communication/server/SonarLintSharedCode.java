package name.remal.gradle_plugins.sonarlint.communication.server;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempDirectory;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.NONE;
import static name.remal.gradle_plugins.sonarlint.communication.server.LogOutputViaSlf4j.LOG_OUTPUT_VIA_SLF4J;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyListProxy;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyMapProxy;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrow;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.toolkit.ClosablesContainer;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.ExtendedRepository;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;
import org.sonarsource.sonarlint.core.rule.extractor.RuleDefinitionsLoader;

@RequiredArgsConstructor
@Getter
public class SonarLintSharedCode implements AutoCloseable {

    private final SonarLintParams params;


    private static final Logger logger = LoggerFactory.getLogger(SonarLintSharedCode.class);

    @Getter(lazy = true)
    private final PluginsLoadResult loadedPlugins = loadPlugins();

    private PluginsLoadResult loadPlugins() {
        return withThreadLogger(null, () -> {
            var pluginJarLocations = getParams().getPluginFiles().stream()
                .map(File::toPath)
                .distinct()
                .filter(path -> {
                    try {
                        var pluginInfo = PluginInfo.create(path);
                        logger.debug("plugin={}: {}", pluginInfo, path);
                        return true;
                    } catch (Exception e) {
                        logger.debug("not a plugin: " + path, e);
                        return false;
                    }
                })
                .collect(toImmutableSet());

            var sonarLanguages = getParams().getEnabledPluginLanguages().stream()
                .map(SonarLintLanguageConverter::convertSonarLintLanguage)
                .collect(toImmutableSet());

            var pluginsConfig = new Configuration(
                pluginJarLocations,
                sonarLanguages,
                true,
                Optional.of(Version.create("9999.9999.9999"))
            );

            var loadedPlugins = new PluginsLoader().load(pluginsConfig, Set.of());
            registerCloseable(loadedPlugins.getLoadedPlugins()::close);
            return loadedPlugins;
        });
    }


    @Getter(lazy = true)
    private final Path tempDir = createTempDir();

    @SneakyThrows
    @SuppressWarnings("java:S5443")
    private Path createTempDir() {
        var dir = createTempDirectory(getClass().getName() + "-");
        registerCloseable(() -> {
            if (!tryToDeleteRecursively(dir)) {
                // ignore failure
            }
        });
        return dir;
    }


    @Getter(lazy = true)
    private final GlobalAnalysisContainer analysisContainer = createAnalysisContainer();

    @SneakyThrows
    private GlobalAnalysisContainer createAnalysisContainer() {
        return withThreadLogger(null, () -> {
            var workDir = createDirectories(getTempDir().resolve("work"));
            var homeDir = createDirectories(getTempDir().resolve("home"));

            var analysisSchedulerConfiguration = AnalysisSchedulerConfiguration.builder()
                .setWorkDir(workDir)
                .setExtraProperties(Map.of(
                    "sonar.userHome", homeDir.toString()
                ))
                .setClientPid(-1)
                .setModulesProvider(List::of)
                .build();

            var container = new GlobalAnalysisContainer(
                analysisSchedulerConfiguration,
                getLoadedPlugins().getLoadedPlugins()
            );
            container.startComponents();
            registerCloseable(container::stopComponents);
            return container;
        });
    }

    @Getter(lazy = true)
    private final DefinitionsExtractorContainer definitionsExtractorContainer = createDefinitionsExtractorContainer();

    private DefinitionsExtractorContainer createDefinitionsExtractorContainer() {
        return withThreadLogger(null, () -> {
            var container = new DefinitionsExtractorContainer(getAnalysisContainer());
            container.startComponents();
            registerCloseable(container::stopComponents);
            return container;
        });
    }

    @Unmodifiable
    private final List<PropertyDefinition> allPropertyDefinitions = asLazyListProxy(() -> withThreadLogger(null, () -> {
        var definitions = getDefinitionsExtractorContainer()
            .getComponentByType(PropertyDefinitions.class)
            .getAll();

        return List.copyOf(definitions);
    }));

    @Unmodifiable
    private final Map<RuleKey, RulesDefinition.Rule> allRules = asLazyMapProxy(() -> withThreadLogger(null, () -> {
        var rulesDefinitionContext = getDefinitionsExtractorContainer()
            .getComponentByType(RuleDefinitionsLoader.class)
            .getContext();

        return rulesDefinitionContext.repositories().stream()
            .filter(not(Repository::isExternal))
            .map(ExtendedRepository::rules)
            .flatMap(Collection::stream)
            .collect(toImmutableMap(
                rule -> RuleKey.of(rule.repository().key(), rule.key()),
                identity(),
                (oldRule, rule) -> rule
            ));
    }));


    //#region close

    @Getter(NONE)
    private final ClosablesContainer closeables = new ClosablesContainer();

    @SuppressWarnings("UnusedReturnValue")
    private <T extends AutoCloseable> T registerCloseable(T closeable) {
        return closeables.registerCloseable(closeable);
    }

    @Override
    public final void close() {
        closeables.close();
    }

    //#endregion


    //#region Utils

    @SneakyThrows
    protected static <T> T withThreadLogger(@Nullable LogMessageConsumer logMessageConsumer, Callable<T> action) {
        var logger = SonarLintLogger.get();
        var prevTarget = logger.getTargetForCopy();
        if (prevTarget instanceof LogOutputViaConsumer) {
            return action.call();
        }

        var newTarget = logMessageConsumer != null
            ? new LogOutputViaConsumer(logMessageConsumer)
            : LOG_OUTPUT_VIA_SLF4J;
        logger.setTarget(newTarget);
        try {
            return action.call();

        } finally {
            logger.setTarget(prevTarget);
        }
    }

    static {
        try {
            new URL("jar", "", "file:test.jar!/resource.txt").openConnection().setDefaultUseCaches(false);
        } catch (IOException e) {
            throw sneakyThrow(e);
        }
    }

    //#endregion

}
