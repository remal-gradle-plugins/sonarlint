package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.ANALYSE;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.HELP_PROPERTIES;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.HELP_RULES;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintServices.loadSonarLintService;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.ProxyUtils.toDynamicInterface;

import java.util.Objects;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintPropertiesDocumentationCollector;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintRulesDocumentationCollector;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.issues.TextIssuesRenderer;
import org.gradle.workers.WorkAction;

@NoArgsConstructor(onConstructor_ = {@Inject})
@CustomLog
abstract class SonarLintAction implements WorkAction<SonarLintExecutionParams> {

    @Override
    @SneakyThrows
    @SuppressWarnings("java:S3776")
    public void execute() {
        val params = getParameters();

        val command = params.getCommand().get();
        if (command == ANALYSE) {
            val sonarLintAnalyzer = loadSonarLintService(
                SonarLintAnalyzer.class,
                params.getSonarLintVersion().get()
            );
            val untypedIssues = sonarLintAnalyzer.analyze(params);
            val issues = untypedIssues.stream()
                .filter(Objects::nonNull)
                .map(untypedIssue -> toDynamicInterface(untypedIssue, Issue.class))
                .collect(toList());

            val xmlReportLocation = params.getXmlReportLocation().getAsFile().getOrNull();
            if (xmlReportLocation != null) {
                new CheckstyleXmlIssuesRenderer().renderIssuesToFile(issues, xmlReportLocation);
            }

            val htmlReportLocation = params.getHtmlReportLocation().getAsFile().getOrNull();
            if (htmlReportLocation != null) {
                new CheckstyleHtmlIssuesRenderer("SonarLint").renderIssuesToFile(issues, htmlReportLocation);
            }

            if (isNotEmpty(issues)) {
                logger.error(
                    new TextIssuesRenderer()
                        .withDescription(params.getWithDescription().getOrElse(true))
                        .renderIssues(issues)
                );

                if (!params.getIsIgnoreFailures().get()) {
                    throw new AssertionError(format(
                        "SonarLint analysis failed with %d issues",
                        issues.size()
                    ));
                }
            }

        } else if (command == HELP_RULES) {
            val rulesDocumentationCollector = loadSonarLintService(
                SonarLintRulesDocumentationCollector.class,
                params.getSonarLintVersion().get()
            );
            val rulesDoc = rulesDocumentationCollector.collectRulesDocumentation(params);
            logger.quiet(rulesDoc.renderToText());

        } else if (command == HELP_PROPERTIES) {
            val propertiesDocumentationCollector = loadSonarLintService(
                SonarLintPropertiesDocumentationCollector.class,
                params.getSonarLintVersion().get()
            );
            val propertiesDoc = propertiesDocumentationCollector.collectPropertiesDocumentation(params);
            logger.quiet(propertiesDoc.renderToText());

        } else {
            throw new AssertionError("Unsupported SonarLint runner command: " + command);
        }
    }

}
