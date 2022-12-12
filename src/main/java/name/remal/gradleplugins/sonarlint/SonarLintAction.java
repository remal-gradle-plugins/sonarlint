package name.remal.gradleplugins.sonarlint;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradleplugins.sonarlint.internal.SonarLintCommand.ANALYSE;
import static name.remal.gradleplugins.sonarlint.internal.SonarLintCommand.HELP_PROPERTIES;
import static name.remal.gradleplugins.sonarlint.internal.SonarLintCommand.HELP_RULES;
import static name.remal.gradleplugins.toolkit.CrossCompileServices.loadCrossCompileService;
import static name.remal.gradleplugins.toolkit.CrossCompileVersionComparator.CrossCompileVersionComparisonResult.compareDependencyVersionToCurrentVersionObjects;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradleplugins.toolkit.ProxyUtils.toDynamicInterface;

import java.util.Objects;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradleplugins.sonarlint.internal.Documentation;
import name.remal.gradleplugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradleplugins.sonarlint.internal.SonarLintExecutor;
import name.remal.gradleplugins.toolkit.Version;
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

        val sonarLintExecutor = loadCrossCompileService(SonarLintExecutor.class, (dependency, versionString) -> {
            if (dependency.equals("sonarlint")) {
                val version = Version.parse(versionString);
                long majorVersion = version.getNumber(0);
                long currentMajorVersion = params.getSonarLintMajorVersion().get();
                return compareDependencyVersionToCurrentVersionObjects(majorVersion, currentMajorVersion);

            } else {
                return null;
            }
        });

        sonarLintExecutor.init(params);

        val command = params.getCommand().get();
        if (command == ANALYSE) {
            val untypedIssues = sonarLintExecutor.analyze();
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
            val untypedRulesDoc = sonarLintExecutor.collectRulesDocumentation();
            val rulesDoc = toDynamicInterface(untypedRulesDoc, Documentation.class);
            logger.warn(rulesDoc.renderToText());

        } else if (command == HELP_PROPERTIES) {
            val untypedPropertiesDoc = sonarLintExecutor.collectPropertiesDocumentation();
            val propertiesDoc = toDynamicInterface(untypedPropertiesDoc, Documentation.class);
            logger.warn(propertiesDoc.renderToText());

        } else {
            throw new AssertionError("Unsupported SonarLint runner command: " + command);
        }
    }

}
