package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintConfigurationUtils.createEngineConfig;
import static name.remal.gradle_plugins.toolkit.PredicateUtils.not;
import static name.remal.gradle_plugins.toolkit.issues.Issue.newIssue;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.ERROR;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.INFO;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.WARNING;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.issues.HtmlMessage;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.issues.TextMessage;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.commons.RuleKey;

public class SonarLintAnalyzer {

    @SuppressWarnings("java:S3776")
    public Collection<Issue> analyze(SonarLintExecutionParams params) {
        val engineConfig = createEngineConfig(params);

        val analysisConfig = StandaloneAnalysisConfiguration.builder()
            .setBaseDir(params.getProjectDir().get().getAsFile().toPath())
            .addInputFiles(params.getSourceFiles().get().stream()
                .map(GradleClientInputFile::new)
                .collect(toList())
            )
            .addIncludedRules(params.getEnabledRules().get().stream()
                .map(RuleKey::parse)
                .collect(toList())
            )
            .addExcludedRules(params.getDisabledRules().get().stream()
                .map(RuleKey::parse)
                .collect(toList())
            )
            .putAllExtraProperties(params.getSonarProperties().get())
            .addRuleParameters(params.getRulesProperties().get().entrySet().stream().collect(toMap(
                entry -> RuleKey.parse(entry.getKey()),
                Entry::getValue
            )))
            .build();

        Predicate<SourceFile> isIgnoredSourceFile = sourceFile -> {
            if (sourceFile.isGenerated()) {
                return params.getIsGeneratedCodeIgnored().getOrElse(true);
            }

            return false;
        };

        val engine = new StandaloneSonarLintEngineImpl(engineConfig);
        try {
            Collection<Issue> issues = new LinkedHashSet<>();
            IssueListener issueListener = sonarIssue -> {
                synchronized (issues) {
                    val sourceFile = Optional.ofNullable(sonarIssue.getInputFile())
                        .map(ClientInputFile::getClientObject)
                        .filter(SourceFile.class::isInstance)
                        .map(SourceFile.class::cast)
                        .filter(not(isIgnoredSourceFile))
                        .map(SourceFile::getAbsolutePath)
                        .map(File::new)
                        .orElse(null);
                    if (sourceFile == null) {
                        return;
                    }

                    val message = Optional.ofNullable(sonarIssue.getMessage())
                        .filter(ObjectUtils::isNotEmpty)
                        .map(TextMessage::textMessageOf)
                        .orElse(null);
                    if (message == null) {
                        return;
                    }

                    val issue = newIssue(builder -> {
                        builder.rule(sonarIssue.getRuleKey());
                        builder.message(message);

                        builder.sourceFile(sourceFile);
                        builder.startLine(sonarIssue.getStartLine());
                        builder.startColumn(sonarIssue.getStartLineOffset());
                        builder.endLine(sonarIssue.getEndLine());
                        builder.endColumn(sonarIssue.getEndLineOffset());

                        Optional.ofNullable(sonarIssue.getSeverity())
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

                        Optional.ofNullable(sonarIssue.getType())
                            .map(Enum::name)
                            .map(UPPER_UNDERSCORE.converterTo(UPPER_CAMEL))
                            .ifPresent(builder::category);

                        engine.getRuleDetails(sonarIssue.getRuleKey())
                            .map(StandaloneRuleDetails::getHtmlDescription)
                            .map(HtmlMessage::htmlMessageOf)
                            .ifPresent(builder::description);
                    });
                    issues.add(issue);
                }
            };

            engine.analyze(analysisConfig, issueListener, engineConfig.getLogOutput(), new GradleProgressMonitor());

            return issues;

        } finally {
            engine.stop();
        }
    }

}
