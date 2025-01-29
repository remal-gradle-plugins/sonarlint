package name.remal.gradle_plugins.sonarlint.internal.sonar;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Predicates.alwaysTrue;
import static java.nio.file.Files.createDirectories;
import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.internal.sonar.SimpleLogOutput.SIMPLE_LOG_OUTPUT;
import static name.remal.gradle_plugins.sonarlint.internal.sonar.SimpleProgressMonitor.SIMPLE_PROGRESS_MONITOR;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.issues.Issue.newIssue;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.ERROR;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.INFO;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.WARNING;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.issues.HtmlMessage;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.issues.TextMessage;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;

public class SonarLintServiceAnalysis
    extends AbstractSonarLintService<SonarLintServiceAnalysisParams> {

    public SonarLintServiceAnalysis(SonarLintServiceAnalysisParams params) {
        super(params);
    }

    private final LazyValue<GlobalAnalysisContainer> analysisContainer = lazyValue(() -> {
        var analysisEngineConfiguration = AnalysisEngineConfiguration.builder()
            .setWorkDir(createDirectories(params.getWorkDir()))
            .setClientPid(-1)
            .setExtraProperties(Map.of(
                "sonar.userHome", params.getSonarUserHome().toString()
            ))
            .setNodeJs(params.getNodeJsExecutable())
            .setModulesProvider(List::of)
            .build();


        var container = new GlobalAnalysisContainer(
            analysisEngineConfiguration,
            loadedPlugins.get().getLoadedPlugins()
        );
        container.startComponents();
        registerCloseable(container::stopComponents);
        return container;
    });

    @SuppressWarnings({"java:S3776", "EnumOrdinal"})
    public Collection<Issue> analyze(
        Collection<SimpleClientInputFile> inputFiles,
        Map<String, String> sonarProperties,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig,
        boolean generatedCodeIgnored
    ) {
        if (inputFiles.isEmpty()) {
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
            .setBaseDir(params.getRepositoryRoot())
            .addInputFiles(inputFiles)
            .putAllExtraProperties(sonarProperties)
            .addActiveRules(activeRules)
            .build();

        Collection<Issue> issues = new LinkedHashSet<>();
        Consumer<org.sonarsource.sonarlint.core.analysis.api.Issue> issueListener = sonarIssue -> {
            synchronized (issues) {
                var sourceFile = Optional.ofNullable(sonarIssue.getInputFile())
                    .map(ClientInputFile::getClientObject)
                    .filter(SimpleClientInputFile.class::isInstance)
                    .map(SimpleClientInputFile.class::cast)
                    .filter(generatedCodeIgnored ? not(SimpleClientInputFile::isGenerated) : alwaysTrue())
                    .map(SimpleClientInputFile::getPath)
                    .map(Path::toFile)
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
            SIMPLE_LOG_OUTPUT
        );
        analyzeCommand.execute(
            analysisContainer.get().getModuleRegistry(),
            SIMPLE_PROGRESS_MONITOR
        );

        return issues;
    }

}
