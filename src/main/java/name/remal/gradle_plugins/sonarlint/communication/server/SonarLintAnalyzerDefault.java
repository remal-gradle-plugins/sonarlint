package name.remal.gradle_plugins.sonarlint.communication.server;

import static com.google.common.base.Predicates.alwaysFalse;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Map.Entry;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.communication.server.SimpleProgressMonitor.SIMPLE_PROGRESS_MONITOR;
import static name.remal.gradle_plugins.sonarlint.communication.server.SonarLintSharedCode.withThreadLogger;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintLanguageIncludes.getLanguageRelativePathPredicate;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import com.google.common.annotations.VisibleForTesting;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguageType;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintAnalyzeParams;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintLogSink;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;

@RequiredArgsConstructor
public class SonarLintAnalyzerDefault implements SonarLintAnalyzer {

    private final SonarLintSharedCode shared;


    @Override
    public Collection<Issue> analyze(
        SonarLintAnalyzeParams params,
        @Nullable SonarLintLogSink logSink
    ) throws RemoteException {
        var repositoryRoot = params.getRepositoryRoot();
        var moduleId = params.getModuleId();
        var sourceFiles = params.getSourceFiles();
        var enabledLanguages = params.getEnabledLanguages();
        var sonarProperties = params.getSonarProperties();
        var enableRulesActivatedByDefault = params.isEnableRulesActivatedByDefault();
        var enabledRulesConfig = params.getEnabledRulesConfig();
        var disabledRulesConfig = params.getDisabledRulesConfig();
        var rulesPropertiesConfig = params.getRulesPropertiesConfig();

        if (sourceFiles.isEmpty()
            || enabledLanguages.isEmpty()
            || (!enableRulesActivatedByDefault && enabledRulesConfig.isEmpty())
        ) {
            return List.of();
        }

        var activeRules = getActiveRules(
            enabledLanguages,
            enableRulesActivatedByDefault,
            enabledRulesConfig,
            disabledRulesConfig,
            rulesPropertiesConfig
        );
        if (activeRules.isEmpty()) {
            return List.of();
        }

        LogMessageConsumer logMessageConsumer = logSink == null ? null : (level, message) -> {
            logSink.onMessage(SonarLintAnalyzerDefault.class.getName(), level, message);
        };
        return withThreadLogger(logMessageConsumer, () ->
            withSingleThreadedFirstFrontendScan(enabledLanguages, sourceFiles, sonarProperties, () -> {
                var inputFiles = sourceFiles.stream()
                    .map(SimpleClientInputFile::new)
                    .map(ClientInputFile.class::cast)
                    .collect(toUnmodifiableList());

                var analysisConfiguration = AnalysisConfiguration.builder()
                    .setBaseDir(repositoryRoot.toPath())
                    .addInputFiles(inputFiles)
                    .putAllExtraProperties(sonarProperties)
                    .addActiveRules(activeRules)
                    .build();

                Collection<Issue> issues = new LinkedHashSet<>();
                var issueConverter = new SonarIssueConverter(shared.getAllRules());
                Consumer<org.sonarsource.sonarlint.core.analysis.api.Issue> issueListener = sonarIssue -> {
                    synchronized (issues) {
                        var issue = issueConverter.convert(sonarIssue);
                        if (issue != null) {
                            issues.add(issue);
                        }
                    }
                };

                var moduleRegistry = shared.getAnalysisContainer().getModuleRegistry();

                var clientFileSystem = new SimpleClientModuleFileSystem(inputFiles);
                var moduleInfo = new ClientModuleInfo(moduleId, clientFileSystem);
                moduleRegistry.registerModule(moduleInfo);

                try {
                    var moduleContainer = requireNonNull(moduleRegistry.getContainerFor(moduleId));
                    moduleContainer.analyze(
                        analysisConfiguration,
                        issueListener,
                        SIMPLE_PROGRESS_MONITOR,
                        null
                    );

                } finally {
                    moduleRegistry.unregisterModule(moduleId);
                }

                return issues;
            })
        );
    }

    @Unmodifiable
    private Collection<ActiveRule> getActiveRules(
        Set<SonarLintLanguage> enabledLanguages,
        boolean enableRulesActivatedByDefault,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig
    ) {
        var enabledRules = getEnabledRules(
            enabledLanguages,
            enableRulesActivatedByDefault,
            enabledRulesConfig,
            disabledRulesConfig
        );

        var rulesProperties = getRuleProperties(rulesPropertiesConfig);
        return enabledRules.entrySet().stream()
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
    }

    @Unmodifiable
    protected static Map<RuleKey, Map<String, String>> getRuleProperties(
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

    @Unmodifiable
    @VisibleForTesting
    Map<RuleKey, Rule> getEnabledRules(
        Set<SonarLintLanguage> enabledLanguages,
        boolean enableRulesActivatedByDefault,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig
    ) {
        var enabledLanguageIds = enabledLanguages.stream()
            .map(SonarLintLanguageConverter::convertSonarLintLanguage)
            .flatMap(lang -> Stream.of(lang.name(), lang.getSonarLanguageKey()))
            .map(String::toLowerCase)
            .collect(toImmutableSet());

        var enabledRules = getRulesKeys(enabledRulesConfig);
        var disabledRules = getRulesKeys(disabledRulesConfig);

        return shared.getAllRules().entrySet().stream()
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
    }

    @Unmodifiable
    protected static Set<RuleKey> getRulesKeys(Collection<String> ruleKeyStrings) {
        return ruleKeyStrings.stream()
            .filter(ObjectUtils::isNotEmpty)
            .map(RuleKey::parse)
            .collect(toImmutableSet());
    }


    private final Object frontendScanMutex = new Object[0];

    @SneakyThrows
    private <T> T withSingleThreadedFirstFrontendScan(
        Set<SonarLintLanguage> enabledLanguages,
        Collection<SourceFile> sourceFiles,
        Map<String, String> sonarProperties,
        Callable<T> action
    ) {
        var enabledFrontendLanguages = enabledLanguages.stream()
            .filter(lang -> lang.getType() == SonarLintLanguageType.FRONTEND)
            .collect(toUnmodifiableList());
        if (enabledFrontendLanguages.isEmpty()) {
            return action.call();
        }

        Predicate<String> frontendRelativePathPredicate = alwaysFalse();
        for (var lang : enabledFrontendLanguages) {
            var langPredicate = getLanguageRelativePathPredicate(lang, sonarProperties);
            frontendRelativePathPredicate = frontendRelativePathPredicate.or(langPredicate);
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

}
