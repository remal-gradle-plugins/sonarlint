package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SimpleProgressMonitor.SIMPLE_PROGRESS_MONITOR;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import name.remal.gradle_plugins.sonarlint.internal.SourceFileInterface;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.issues.Issue;
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
        var analysisSchedulerConfiguration = AnalysisSchedulerConfiguration.builder()
            .setWorkDir(params.getWorkDir())
            .setExtraProperties(Map.of(
                "sonar.userHome", params.getSonarUserHome().toString()
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

    @SuppressWarnings("java:S3776")
    public Collection<Issue> analyze(
        Path repositoryRoot,
        Collection<? extends SourceFileInterface> sourceFiles,
        Map<String, String> sonarProperties,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig
    ) {
        if (sourceFiles.isEmpty()) {
            return List.of();
        }

        var inputFiles = sourceFiles.stream()
            .filter(Objects::nonNull)
            .map(SimpleClientInputFile::new)
            .map(ClientInputFile.class::cast)
            .collect(toUnmodifiableList());

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
    }

}
