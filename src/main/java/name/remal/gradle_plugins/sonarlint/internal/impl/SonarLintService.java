package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.file.Files.createDirectories;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SimpleLogOutput.SIMPLE_LOG_OUTPUT;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SimpleProgressMonitor.SIMPLE_PROGRESS_MONITOR;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyListProxy;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyMapProxy;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.issues.Issue.newIssue;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.ERROR;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.INFO;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.WARNING;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.ClosablesContainer;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.issues.HtmlMessage;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.issues.TextMessage;
import org.jetbrains.annotations.Unmodifiable;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.ExtendedRepository;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;
import org.sonarsource.sonarlint.core.rule.extractor.RuleDefinitionsLoader;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

@SuperBuilder
@CustomLog
public class SonarLintService extends SonarLintServiceParams implements AutoCloseable {

    static {
        SonarLintLogger.setTarget(SIMPLE_LOG_OUTPUT);
    }


    private final ClosablesContainer closables = new ClosablesContainer();

    @Override
    public void close() {
        closables.close();
    }


    private final LazyValue<PluginsLoadResult> loadedPlugins = lazyValue(() -> {
        var pluginJarLocations = pluginsClasspath.stream()
            .filter(Objects::nonNull)
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

        var enabledLanguages = stream(SonarLanguage.values())
            .filter(language -> includedLanguages.isEmpty() || isLanguageInFilter(language, includedLanguages))
            .filter(language -> excludedLanguages.isEmpty() || !isLanguageInFilter(language, excludedLanguages))
            .collect(toImmutableSet());

        var nodeJsVersion = Optional.ofNullable(nodeJsInfo)
            .map(NodeJsFound::getVersion)
            .map(Version::create)
            .orElse(null);

        var pluginsConfig = new Configuration(
            pluginJarLocations,
            enabledLanguages,
            true,
            Optional.ofNullable(nodeJsVersion)
        );

        var loadedPlugins = new PluginsLoader().load(pluginsConfig, Set.of());
        closables.registerCloseable(loadedPlugins);
        return loadedPlugins;
    });

    private final LazyValue<ExtendedGlobalAnalysisContainer> container = lazyValue(() -> {
        var analysisEngineConfiguration = AnalysisEngineConfiguration.builder()
            .setWorkDir(createDirectories(workDir))
            .setClientPid(-1)
            .setExtraProperties(Map.of())
            .setNodeJs(Optional.ofNullable(nodeJsInfo)
                .map(NodeJsFound::getExecutable)
                .map(File::toPath)
                .orElse(null)
            )
            .setModulesProvider(List::of)
            .build();


        var container = new ExtendedGlobalAnalysisContainer(
            analysisEngineConfiguration,
            loadedPlugins.get().getLoadedPlugins()
        );
        closables.registerCloseable(container::stopComponents);
        return container;
    });


    private final Map<RuleKey, RulesDefinition.Rule> allRules = asLazyMapProxy(() -> {
        var extractor = new RulesDefinitionExtractorContainer(
            loadedPlugins.get().getLoadedPlugins().getAllPluginInstancesByKeys()
        );
        extractor.startComponents();
        try {
            var rulesDefinitionContext = extractor.getComponentByType(RuleDefinitionsLoader.class).getContext();
            return rulesDefinitionContext.repositories().stream()
                .filter(not(Repository::isExternal))
                .map(ExtendedRepository::rules)
                .flatMap(Collection::stream)
                .collect(toImmutableMap(
                    rule -> RuleKey.of(rule.repository().key(), rule.key()),
                    identity(),
                    (oldRule, rule) -> rule
                ));

        } finally {
            extractor.stopComponents();
        }
    });

    private final List<PropertyDefinition> allPropertyDefinitions = asLazyListProxy(() -> {
        var definitions = container.get().startComponents()
            .getComponentByType(PropertyDefinitions.class)
            .getAll();

        return List.copyOf(definitions);
    });


    @SuppressWarnings({"java:S3776", "EnumOrdinal"})
    public Collection<Issue> analyze(
        Path baseDir,
        Map<String, String> sonarProperties,
        List<SourceFile> sourceFiles,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig,
        boolean generatedCodeIgnored
    ) {
        var filesToAnalyze = sourceFiles.stream()
            .filter(Objects::nonNull)
            .map(SimpleClientInputFile::new)
            .collect(toUnmodifiableList());
        if (filesToAnalyze.isEmpty()) {
            return List.of();
        }

        var enabledRules = getRulesKeys(enabledRulesConfig);
        var disabledRules = getRulesKeys(disabledRulesConfig);
        var ruleProperties = getRuleProperties(rulesPropertiesConfig);
        var activeRules = allRules.entrySet().stream()
            .map(entry -> {
                var ruleKey = entry.getKey();
                var rule = entry.getValue();

                if (disabledRules.contains(ruleKey)) {
                    return null;
                }
                if (!rule.activatedByDefault() && !enabledRules.contains(ruleKey)) {
                    return null;
                }

                var activeRule = new ActiveRule(ruleKey.toString(), rule.repository().language());
                activeRule.setParams(ruleProperties.getOrDefault(ruleKey, emptyMap()));
                return activeRule;
            })
            .filter(Objects::nonNull)
            .collect(toUnmodifiableList());

        var analysisConfiguration = AnalysisConfiguration.builder()
            .setBaseDir(baseDir)
            .addInputFiles(filesToAnalyze)
            .putAllExtraProperties(sonarProperties)
            .addActiveRules(activeRules)
            .build();

        Collection<Issue> issues = new LinkedHashSet<>();
        Consumer<org.sonarsource.sonarlint.core.analysis.api.Issue> issueListener = sonarIssue -> {
            synchronized (issues) {
                var sourceFile = Optional.ofNullable(sonarIssue.getInputFile())
                    .map(ClientInputFile::getClientObject)
                    .filter(SourceFile.class::isInstance)
                    .map(SourceFile.class::cast)
                    .filter(not(generatedCodeIgnored ? SourceFile::isGenerated : __ -> false))
                    .map(SourceFile::getAbsolutePath)
                    .map(File::new)
                    .orElse(null);
                if (sourceFile == null) {
                    return;
                }

                var message = Optional.ofNullable(sonarIssue.getMessage())
                    .filter(ObjectUtils::isNotEmpty)
                    .map(TextMessage::textMessageOf)
                    .orElse(null);
                if (message == null) {
                    return;
                }

                var issue = newIssue(builder -> {
                    builder.rule(sonarIssue.getRuleKey());
                    builder.message(message);

                    builder.sourceFile(sourceFile);
                    builder.startLine(sonarIssue.getStartLine());
                    builder.startColumn(sonarIssue.getStartLineOffset());
                    builder.endLine(sonarIssue.getEndLine());
                    builder.endColumn(sonarIssue.getEndLineOffset());


                    var rule = Optional.ofNullable(sonarIssue.getRuleKey())
                        .map(RuleKey::parse)
                        .map(allRules::get)
                        .orElse(null);

                    Map<Enum<?>, Enum<?>> impacts = new LinkedHashMap<>();
                    if (sonarIssue.getOverriddenImpacts() != null) {
                        impacts.putAll(sonarIssue.getOverriddenImpacts());
                    }
                    if (impacts.isEmpty() && rule != null) {
                        impacts.putAll(rule.defaultImpacts());
                    }
                    Enum<?> impactSeverity = null;
                    Enum<?> softwareQuality = null;
                    for (var entry : impacts.entrySet()) {
                        if (impactSeverity == null
                            || impactSeverity.ordinal() < entry.getValue().ordinal()
                        ) {
                            impactSeverity = entry.getValue();
                            softwareQuality = entry.getKey();
                        }
                    }

                    Optional.ofNullable(impactSeverity)
                        .map(Enum::name)
                        .map(String::toUpperCase)
                        .ifPresent(severity -> {
                            switch (severity) {
                                case "BLOCKER":
                                case "CRITICAL":
                                case "MAJOR":
                                case "HIGH":
                                    builder.severity(ERROR);
                                    break;
                                case "MINOR":
                                case "MEDIUM":
                                    builder.severity(WARNING);
                                    break;
                                default:
                                    builder.severity(INFO);
                            }
                        });

                    Optional.ofNullable(softwareQuality)
                        .map(Enum::name)
                        .map(UPPER_UNDERSCORE.converterTo(UPPER_CAMEL))
                        .ifPresent(builder::category);

                    Optional.ofNullable(rule)
                        .map(Rule::htmlDescription)
                        .map(HtmlMessage::htmlMessageOf)
                        .ifPresent(builder::description);
                });
                issues.add(issue);
            }
        };

        var analyzeCommand = new AnalyzeCommand(
            null,
            analysisConfiguration,
            issueListener,
            SIMPLE_LOG_OUTPUT,
            null
        );
        analyzeCommand.execute(
            container.get().startComponents().getModuleRegistry(),
            SIMPLE_PROGRESS_MONITOR
        );

        return issues;
    }


    @SneakyThrows
    public RulesDocumentation collectRulesDocumentation(
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig
    ) {
        var enabledRules = getRulesKeys(enabledRulesConfig);
        var disabledRules = getRulesKeys(disabledRulesConfig);
        var ruleProperties = getRuleProperties(rulesPropertiesConfig);

        var rulesDoc = new RulesDocumentation();
        allRules.forEach((key, rule) -> rulesDoc.rule(key.toString(), ruleDoc -> {
            ruleDoc.setName(rule.name());

            if (disabledRules.contains(key)) {
                ruleDoc.setStatus(DISABLED_EXPLICITLY);
            } else if (enabledRules.contains(key)) {
                ruleDoc.setStatus(ENABLED_EXPLICITLY);
            } else if (rule.activatedByDefault()) {
                ruleDoc.setStatus(ENABLED_BY_DEFAULT);
            } else {
                ruleDoc.setStatus(DISABLED_BY_DEFAULT);
            }

            Optional.ofNullable(rule.repository().language())
                .flatMap(SonarLanguage::forKey)
                .map(SonarLanguage::getSonarLanguageKey)
                .ifPresent(ruleDoc::setLanguage);

            rule.params().forEach(param -> ruleDoc.param(param.key(), paramDoc -> {
                paramDoc.setDescription(param.description());
                Optional.ofNullable(param.type())
                    .map(RuleParamType::type)
                    .ifPresent(paramDoc::setType);
                paramDoc.setCurrentValue(ruleProperties.getOrDefault(key, emptyMap()).get(param.key()));
                paramDoc.setDefaultValue(param.defaultValue());
                paramDoc.setPossibleValues(param.type().values());
            }));
        }));
        return rulesDoc;
    }


    public PropertiesDocumentation collectPropertiesDocumentation(
        Map<String, String> sonarProperties
    ) {
        var propertiesDoc = new PropertiesDocumentation();
        allPropertyDefinitions.forEach(propDef -> propertiesDoc.property(propDef.key(), propDoc -> {
            propDoc.setName(propDef.name());
            propDoc.setDescription(propDef.description());
            Optional.ofNullable(propDef.type())
                .map(Enum::name)
                .ifPresent(propDoc::setType);
            propDoc.setCurrentValue(sonarProperties.get(propDef.key()));
            propDoc.setDefaultValue(propDef.defaultValue());
        }));
        return propertiesDoc;
    }


    private static boolean isLanguageInFilter(SonarLanguage language, Collection<String> filter) {
        return filter.stream().anyMatch(id ->
            id.equalsIgnoreCase(language.getSonarLanguageKey())
                || id.equalsIgnoreCase(language.getPluginKey())
                || id.equalsIgnoreCase(language.name())
        );
    }

    @Unmodifiable
    private static Set<RuleKey> getRulesKeys(Collection<String> ruleKeyStrings) {
        return ruleKeyStrings.stream()
            .filter(ObjectUtils::isNotEmpty)
            .map(RuleKey::parse)
            .collect(toImmutableSet());
    }

    @Unmodifiable
    private static Map<RuleKey, Map<String, String>> getRuleProperties(
        Map<String, Map<String, String>> rulePropertiesWithStringKeys
    ) {
        return rulePropertiesWithStringKeys.entrySet().stream()
            .filter(entry -> isNotEmpty(entry.getKey()) && isNotEmpty(entry.getValue()))
            .collect(toImmutableMap(
                entry -> RuleKey.parse(entry.getKey()),
                Entry::getValue,
                (oldProps, props) -> props
            ));
    }

}
