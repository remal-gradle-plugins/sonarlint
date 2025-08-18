package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.file.Files.createDirectories;
import static java.util.Map.Entry;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintLanguageIncludes.getLanguageRelativePathPredicates;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SimpleProgressMonitor.SIMPLE_PROGRESS_MONITOR;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguageType;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import org.jetbrains.annotations.Unmodifiable;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;

public class SonarLintServiceAnalysis
    extends AbstractSonarLintService<SonarLintServiceAnalysisParams> {

    public SonarLintServiceAnalysis(SonarLintServiceAnalysisParams params) {
        super(params);
    }

    private final LazyValue<GlobalAnalysisContainer> analysisContainer = lazyValue(() -> {
        createDirectories(params.getSonarUserHome().toPath());

        var analysisSchedulerConfiguration = AnalysisSchedulerConfiguration.builder()
            .setWorkDir(params.getWorkDir().toPath())
            .setExtraProperties(Map.of(
                "sonar.userHome", params.getSonarUserHome().getAbsolutePath()
            ))
            .setClientPid(-1)
            .setModulesProvider(List::of)
            .build();

        var container = new GlobalAnalysisContainer(
            analysisSchedulerConfiguration,
            loadedPlugins.get().getLoadedPlugins()
        );
        container.startComponents();
        registerCloseable(container::stopComponents);
        return container;
    });

    @Unmodifiable
    @VisibleForTesting
    Map<RuleKey, Rule> getEnabledRules(
        Set<SonarLintLanguage> enabledLanguages,
        boolean enableRulesActivatedByDefault,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig
    ) {
        return withThreadLogger(() -> {
            var enabledLanguageIds = enabledLanguages.stream()
                .map(SonarLintLanguageConverter::convertSonarLintLanguage)
                .flatMap(lang -> Stream.of(lang.name(), lang.getSonarLanguageKey()))
                .map(String::toLowerCase)
                .collect(toImmutableSet());

            var enabledRules = getRulesKeys(enabledRulesConfig);
            var disabledRules = getRulesKeys(disabledRulesConfig);

            return allRules.entrySet().stream()
                .filter(entry -> {
                    var ruleKey = entry.getKey();
                    var rule = entry.getValue();

                    var ruleLanguage = rule.repository().language();
                    if (!enabledLanguageIds.contains(ruleLanguage.toLowerCase())) {
                        return false;
                    }

                    if (disabledRules.contains(ruleKey)) {
                        return false;
                    }

                    if (enableRulesActivatedByDefault && rule.activatedByDefault()) {
                        return true;
                    }

                    return enabledRules.contains(ruleKey);
                })
                .collect(toImmutableMap(
                    Entry::getKey,
                    Entry::getValue,
                    (oldRule, rule) -> rule
                ));
        });
    }

    @SuppressWarnings("java:S3776")
    public Collection<Issue> analyze(
        Path repositoryRoot,
        Collection<SourceFile> sourceFiles,
        Map<String, String> sonarProperties,
        boolean enableRulesActivatedByDefault,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig
    ) {
        if (sourceFiles.isEmpty()) {
            return List.of();
        }

        var enabledLanguages = params.getLanguagesToProcess();

        return withThreadLogger(() ->
            withSingleThreadedFirstFrontendScan(enabledLanguages, sourceFiles, sonarProperties, () -> {
                var inputFiles = sourceFiles.stream()
                    .filter(Objects::nonNull)
                    .map(SimpleClientInputFile::new)
                    .map(ClientInputFile.class::cast)
                    .collect(toUnmodifiableList());

                var enabledRules = getEnabledRules(
                    enabledLanguages,
                    enableRulesActivatedByDefault,
                    enabledRulesConfig,
                    disabledRulesConfig
                );
                var rulesProperties = getRuleProperties(rulesPropertiesConfig);
                var activeRules = enabledRules.entrySet().stream()
                    .map(entry -> {
                        var ruleKey = entry.getKey();
                        var rule = entry.getValue();

                        var activeRule = new ActiveRule(ruleKey.toString(), rule.repository().language());

                        var ruleProperties = rulesProperties.get(ruleKey);
                        if (ruleProperties != null) {
                            activeRule.setParams(ruleProperties);
                        }

                        return activeRule;
                    })
                    .collect(toUnmodifiableList());

                var analysisConfiguration = AnalysisConfiguration.builder()
                    .setBaseDir(repositoryRoot)
                    .addInputFiles(inputFiles)
                    .putAllExtraProperties(sonarProperties)
                    .addActiveRules(activeRules)
                    .build();

                Collection<Issue> issues = new LinkedHashSet<>();
                var issueConverter = new SonarIssueConverter(allRules);
                Consumer<org.sonarsource.sonarlint.core.analysis.api.Issue> issueListener = sonarIssue -> {
                    synchronized (issues) {
                        var issue = issueConverter.convert(sonarIssue);
                        if (issue != null) {
                            issues.add(issue);
                        }
                    }
                };

                var moduleRegistry = analysisContainer.get().getModuleRegistry();
                var moduleContainer = moduleRegistry.createTransientContainer(inputFiles);
                moduleContainer.analyze(
                    analysisConfiguration,
                    issueListener,
                    SIMPLE_PROGRESS_MONITOR,
                    null
                );

                return issues;
            })
        );
    }


    //#region Utils

    @SuppressWarnings("UnnecessaryLambda")
    private static final Predicate<String> ALWAYS_FALSE_RELATIVE_PATH_PREDICATE = __ -> false;


    private final Object frontendScanMutex = new Object[0];

    @SneakyThrows
    private <T> T withSingleThreadedFirstFrontendScan(
        Set<SonarLintLanguage> enabledLanguages,
        Collection<SourceFile> sourceFiles,
        Map<String, String> sonarProperties,
        Callable<T> action
    ) {
        var frontendRelativePathPredicate = ALWAYS_FALSE_RELATIVE_PATH_PREDICATE;
        for (var entry : getLanguageRelativePathPredicates(sonarProperties).entrySet()) {
            if (enabledLanguages.contains(entry.getKey())
                && entry.getKey().getType() == SonarLintLanguageType.FRONTEND
            ) {
                frontendRelativePathPredicate = frontendRelativePathPredicate.or(entry.getValue());
            }
        }
        var hasAnyFrontendSourceFile = sourceFiles.stream()
            .map(SourceFile::getRelativePath)
            .anyMatch(frontendRelativePathPredicate);
        if (hasAnyFrontendSourceFile) {
            synchronized (frontendScanMutex) {
                return action.call();
            }
        }

        return action.call();
    }

    //#endregion

}
