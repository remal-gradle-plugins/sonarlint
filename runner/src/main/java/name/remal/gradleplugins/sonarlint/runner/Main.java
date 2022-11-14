package name.remal.gradleplugins.sonarlint.runner;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradleplugins.sonarlint.shared.RunnerCommand.ANALYSE;
import static name.remal.gradleplugins.sonarlint.shared.RunnerCommand.HELP_PROPERTIES;
import static name.remal.gradleplugins.sonarlint.shared.RunnerCommand.HELP_RULES;
import static name.remal.gradleplugins.sonarlint.shared.RunnerParams.readRunnerParamsFrom;
import static name.remal.gradleplugins.toolkit.CrossCompileServices.loadCrossCompileService;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradleplugins.toolkit.ProxyUtils.toDynamicInterface;

import java.nio.file.Paths;
import java.util.Objects;
import lombok.CustomLog;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradleplugins.sonarlint.runner.common.Documentation;
import name.remal.gradleplugins.sonarlint.runner.common.SonarLintExecutor;
import name.remal.gradleplugins.toolkit.Version;
import name.remal.gradleplugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradleplugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradleplugins.toolkit.issues.Issue;
import name.remal.gradleplugins.toolkit.issues.TextIssuesRenderer;
import org.slf4j.bridge.SLF4JBridgeHandler;

@CustomLog
public class Main {

    @SneakyThrows
    public static void main(String[] args) {
        val params = readRunnerParamsFrom(Paths.get(args[0]));

        val sonarLintExecutor = loadCrossCompileService(SonarLintExecutor.class, (dependency, versionString) -> {
            if (dependency.equals("sonarlint")) {
                val version = Version.parse(versionString);
                val majorVersion = version.getNumber(0);
                val currentMajorVersion = params.getSonarLintMajorVersion();
                return Long.compare(majorVersion, currentMajorVersion);

            } else {
                return null;
            }
        });

        sonarLintExecutor.init(params);

        val command = params.getCommand();
        if (command == ANALYSE) {
            val untypedIssues = sonarLintExecutor.analyze();
            val issues = untypedIssues.stream()
                .filter(Objects::nonNull)
                .map(untypedIssue -> toDynamicInterface(untypedIssue, Issue.class))
                .collect(toList());

            val xmlReportLocation = params.getXmlReportLocation();
            if (xmlReportLocation != null) {
                new CheckstyleXmlIssuesRenderer().renderIssuesToPath(issues, xmlReportLocation);
            }

            val htmlReportLocation = params.getHtmlReportLocation();
            if (htmlReportLocation != null) {
                new CheckstyleHtmlIssuesRenderer().renderIssuesToPath(issues, htmlReportLocation);
            }

            if (isNotEmpty(issues)) {
                logger.error(new TextIssuesRenderer().renderIssues(issues));

                if (!params.isIgnoreFailures()) {
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


    static {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.install();
        }
    }

}
