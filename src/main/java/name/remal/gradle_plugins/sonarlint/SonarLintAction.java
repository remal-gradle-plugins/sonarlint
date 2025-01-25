package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.ANALYSE;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.HELP_PROPERTIES;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.HELP_RULES;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.VerificationExceptionUtils.newVerificationException;

import java.io.File;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintService;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.TextIssuesRenderer;
import org.gradle.workers.WorkAction;

@NoArgsConstructor(onConstructor_ = {@Inject})
@CustomLog
abstract class SonarLintAction implements WorkAction<SonarLintExecutionParams> {

    @Override
    @SneakyThrows
    @SuppressWarnings({"java:S3776", "Slf4jFormatShouldBeConst", "java:S5411"})
    public void execute() {
        var params = getParameters();
        try (
            var sonarLintService = SonarLintService.builder()
                .workDir(params.getWorkDir().get().getAsFile().toPath())
                .pluginsClasspath(params.getPluginsClasspath().getFiles().stream()
                    .map(File::toPath)
                    .collect(toList())
                )
                .includedLanguages(params.getIncludedLanguages().get())
                .excludedLanguages(params.getExcludedLanguages().get())
                .nodeJsInfo(params.getNodeJsInfo().getOrNull())
                .build()
        ) {
            var command = params.getCommand().get();
            if (command == ANALYSE) {
                var issues = sonarLintService.analyze(
                    params.getProjectDir().get().getAsFile().toPath(),
                    params.getSonarProperties().get(),
                    params.getSourceFiles().get(),
                    params.getEnabledRules().get(),
                    params.getDisabledRules().get(),
                    params.getRulesProperties().get(),
                    params.getIsGeneratedCodeIgnored().get()
                );

                var xmlReportLocation = params.getXmlReportLocation().getAsFile().getOrNull();
                if (xmlReportLocation != null) {
                    new CheckstyleXmlIssuesRenderer().renderIssuesToFile(issues, xmlReportLocation);
                }

                var htmlReportLocation = params.getHtmlReportLocation().getAsFile().getOrNull();
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
                        throw newVerificationException(format(
                            "SonarLint analysis failed with %d issues",
                            issues.size()
                        ));
                    }
                }

            } else if (command == HELP_RULES) {
                var rulesDoc = sonarLintService.collectRulesDocumentation(
                    params.getEnabledRules().get(),
                    params.getDisabledRules().get(),
                    params.getRulesProperties().get()
                );
                logger.quiet(rulesDoc.renderToText());

            } else if (command == HELP_PROPERTIES) {
                var propertiesDoc = sonarLintService.collectPropertiesDocumentation(
                    params.getSonarProperties().get()
                );
                logger.quiet(propertiesDoc.renderToText());

            } else {
                throw new AssertionError("Unsupported SonarLint runner command: " + command);
            }
        }
    }

}
