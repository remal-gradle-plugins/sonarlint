package name.remal.gradleplugins.sonarlint.internal.latest;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static name.remal.gradleplugins.sonarlint.internal.StandaloneGlobalConfigurationFactory.createEngineConfig;
import static name.remal.gradleplugins.toolkit.PredicateUtils.not;
import static name.remal.gradleplugins.toolkit.issues.Issue.newIssue;
import static name.remal.gradleplugins.toolkit.issues.IssueSeverity.ERROR;
import static name.remal.gradleplugins.toolkit.issues.IssueSeverity.INFO;
import static name.remal.gradleplugins.toolkit.issues.IssueSeverity.WARNING;

import com.google.auto.service.AutoService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.val;
import name.remal.gradleplugins.sonarlint.internal.SonarLintAnalyzer;
import name.remal.gradleplugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradleplugins.sonarlint.internal.SourceFile;
import name.remal.gradleplugins.toolkit.ObjectUtils;
import name.remal.gradleplugins.toolkit.issues.HtmlMessage;
import name.remal.gradleplugins.toolkit.issues.Issue;
import name.remal.gradleplugins.toolkit.issues.TextMessage;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.commons.RuleKey;

@AutoService(SonarLintAnalyzer.class)
final class SonarLintAnalyzerLatest implements SonarLintAnalyzer {

    @Override
    @SuppressWarnings("java:S3776")
    public List<?> analyze(SonarLintExecutionParams params) {
        val engineConfig = createEngineConfig(params);

        val analysisConfig = StandaloneAnalysisConfiguration.builder()
            .setBaseDir(params.getProjectDir().get().getAsFile().toPath())
            .addInputFiles(params.getSourceFiles().getOrElse(emptyList()).stream()
                .map(GradleClientInputFile::new)
                .collect(toList())
            )
            .addIncludedRules(params.getEnabledRules().getOrElse(emptySet()).stream()
                .map(RuleKey::parse)
                .collect(toList())
            )
            .addExcludedRules(params.getDisabledRules().getOrElse(emptySet()).stream()
                .map(RuleKey::parse)
                .collect(toList())
            )
            .putAllExtraProperties(params.getSonarProperties().getOrElse(emptyMap()))
            .addRuleParameters(params.getRulesProperties().getOrElse(emptyMap()).entrySet().stream().collect(toMap(
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
            List<Issue> issues = new ArrayList<>();
            IssueListener issueListener = sonarIssue -> {
                synchronized (issues) {
                    val issue = newIssue(builder -> {
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
                        } else {
                            builder.sourceFile(sourceFile);
                        }

                        val message = Optional.ofNullable(sonarIssue.getMessage())
                            .filter(ObjectUtils::isNotEmpty)
                            .map(TextMessage::textMessageOf)
                            .orElse(null);
                        if (message == null) {
                            return;
                        } else {
                            builder.message(message);
                        }

                        val ruleKey = sonarIssue.getRuleKey();
                        if (ruleKey == null) {
                            return;
                        } else {
                            builder.rule(ruleKey);
                        }

                        Optional.ofNullable(sonarIssue.getSeverity())
                            .map(Enum::name)
                            .map(String::toUpperCase)
                            .ifPresent(severity -> {
                                switch (severity) {
                                    case "BLOCKER":
                                    case "CRITICAL":
                                    case "MAJOR":
                                        builder.severity(ERROR);
                                        break;
                                    case "MINOR":
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

                        builder.startLine(sonarIssue.getStartLine());
                        builder.startColumn(sonarIssue.getStartLineOffset());
                        builder.endLine(sonarIssue.getEndLine());
                        builder.endColumn(sonarIssue.getEndLineOffset());

                        engine.getRuleDetails(ruleKey)
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
