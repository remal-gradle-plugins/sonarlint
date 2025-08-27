package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PUBLIC;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;
import static name.remal.gradle_plugins.toolkit.VerificationExceptionUtils.newVerificationException;

import java.util.LinkedHashSet;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.communication.server.ImmutableSonarLintParams;
import name.remal.gradle_plugins.sonarlint.communication.server.SonarLintAnalyzerDefault;
import name.remal.gradle_plugins.sonarlint.communication.server.SonarLintSharedCode;
import name.remal.gradle_plugins.sonarlint.communication.server.api.ImmutableSonarLintAnalyzeParams;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.TextIssuesRenderer;

@CustomLog
@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class SonarLintAnalyzeWorkAction
    implements AbstractSonarLintTaskWorkAction<SonarLintAnalyzeWorkActionParams> {

    @Override
    @SneakyThrows
    public void execute() {
        var params = getParameters();

        var xmlReportLocation = params.getXmlReportLocation().getAsFile().getOrNull();
        if (xmlReportLocation != null) {
            if (!tryToDeleteRecursively(xmlReportLocation.toPath())) {
                // ignore failures
            }
        }

        var htmlReportLocation = params.getHtmlReportLocation().getAsFile().getOrNull();
        if (htmlReportLocation != null) {
            if (!tryToDeleteRecursively(htmlReportLocation.toPath())) {
                // ignore failures
            }
        }

        var enabledRules = params.getEnabledRules().get();
        var disabledRules = new LinkedHashSet<>(params.getDisabledRules().get());
        params.getAutomaticallyDisabledRules().get().keySet().stream()
            .filter(not(enabledRules::contains))
            .forEach(disabledRules::add);

        var sonarLintParams = ImmutableSonarLintParams.builder()
            .pluginFiles(params.getPluginFiles())
            .enabledPluginLanguages(params.getLanguagesToProcess().get())
            .build();
        try (var shared = new SonarLintSharedCode(sonarLintParams)) {
            var service = new SonarLintAnalyzerDefault(shared);
            var issues = service.analyze(
                ImmutableSonarLintAnalyzeParams.builder()
                    .repositoryRoot(params.getRootDirectory().get().getAsFile())
                    .moduleId(params.getModuleId().get())
                    .sourceFiles(params.getSourceFiles().get())
                    .sonarProperties(params.getSonarProperties().get())
                    .enabledRulesConfig(enabledRules)
                    .disabledRulesConfig(disabledRules)
                    .rulesPropertiesConfig(params.getRulesProperties().get())
                    .build(),
                null
            );

            if (xmlReportLocation != null) {
                new CheckstyleXmlIssuesRenderer().renderIssuesToFile(issues, xmlReportLocation);
            }

            if (htmlReportLocation != null) {
                new CheckstyleHtmlIssuesRenderer("SonarLint").renderIssuesToFile(issues, htmlReportLocation);
            }

            if (!issues.isEmpty()) {
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
        }
    }

}
