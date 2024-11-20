package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.ANALYSE;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.HELP_PROPERTIES;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.HELP_RULES;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintPropertiesDocumentationCollector;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintRulesDocumentationCollector;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.TextIssuesRenderer;
import org.gradle.workers.WorkAction;

@NoArgsConstructor(onConstructor_ = {@Inject})
@CustomLog
abstract class SonarLintAction implements WorkAction<SonarLintExecutionParams> {

    @Override
    @SneakyThrows
    @SuppressWarnings({"java:S3776", "Slf4jFormatShouldBeConst"})
    public void execute() {
        val params = getParameters();

        val command = params.getCommand().get();
        if (command == ANALYSE) {
            val sonarLintAnalyzer = new SonarLintAnalyzer();
            val issues = sonarLintAnalyzer.analyze(params);

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
            val rulesDocumentationCollector = new SonarLintRulesDocumentationCollector();
            val rulesDoc = rulesDocumentationCollector.collectRulesDocumentation(params);
            logger.quiet(rulesDoc.renderToText());

        } else if (command == HELP_PROPERTIES) {
            val propertiesDocumentationCollector = new SonarLintPropertiesDocumentationCollector();
            val propertiesDoc = propertiesDocumentationCollector.collectPropertiesDocumentation(params);
            logger.quiet(propertiesDoc.renderToText());

        } else {
            throw new AssertionError("Unsupported SonarLint runner command: " + command);
        }
    }

}
