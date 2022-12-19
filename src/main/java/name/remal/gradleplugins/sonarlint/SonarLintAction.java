package name.remal.gradleplugins.sonarlint;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradleplugins.sonarlint.internal.SonarLintCommand.ANALYSE;
import static name.remal.gradleplugins.sonarlint.internal.SonarLintCommand.HELP_PROPERTIES;
import static name.remal.gradleplugins.sonarlint.internal.SonarLintCommand.HELP_RULES;
import static name.remal.gradleplugins.sonarlint.internal.SonarLintServices.loadSonarLintService;
import static name.remal.gradleplugins.toolkit.ObjectUtils.defaultValue;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradleplugins.toolkit.ProxyUtils.toDynamicInterface;

import java.util.Objects;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradleplugins.sonarlint.internal.SonarLintAnalyzer;
import name.remal.gradleplugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradleplugins.sonarlint.internal.SonarLintPropertiesDocumentationCollector;
import name.remal.gradleplugins.sonarlint.internal.SonarLintRulesDocumentationCollector;
import name.remal.gradleplugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradleplugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradleplugins.toolkit.issues.Issue;
import name.remal.gradleplugins.toolkit.issues.TextIssuesRenderer;
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
                new CheckstyleHtmlIssuesRenderer().renderIssuesToFile(issues, htmlReportLocation);
            }

            if (isNotEmpty(issues)) {
                logger.error(new TextIssuesRenderer().renderIssues(issues));

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
            logger.warn(rulesDoc.renderToText());

        } else if (command == HELP_PROPERTIES) {
            val propertiesDocumentationCollector = loadSonarLintService(
                SonarLintPropertiesDocumentationCollector.class,
                params.getSonarLintVersion().get()
            );
            val propertiesDoc = propertiesDocumentationCollector.collectPropertiesDocumentation(params);

            propertiesDoc.property("sonar.nodejs.executable", propDef -> {
                propDef.setName("Absolute path to Node.js executable");
                propDef.setType("STRING");
            });

            propertiesDoc.property("sonar.nodejs.version", propDef -> {
                propDef.setName("Node.js executable version");
                propDef.setType("STRING");
                propDef.setDescription("If 'sonar.nodejs.executable' property is not set or empty"
                    + ", a value of this property will be taken as Node.js version"
                );
                propDef.setDefaultValue(defaultValue(params.getDefaultNodeJsVersion().getOrNull()));
            });

            logger.warn(propertiesDoc.renderToText());

        } else {
            throw new AssertionError("Unsupported SonarLint runner command: " + command);
        }
    }

}
