package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.nio.file.Files.createDirectories;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;
import static lombok.AccessLevel.PUBLIC;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;
import static name.remal.gradle_plugins.toolkit.VerificationExceptionUtils.newVerificationException;

import java.io.File;
import java.util.LinkedHashSet;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceAnalysis;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceAnalysisParams;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.TextIssuesRenderer;

@CustomLog
@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class SonarLintAnalyzeWorkAction
    implements AbstractSonarLintWorkAction<SonarLintAnalyzeWorkActionParams> {

    @Override
    @SneakyThrows
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void execute() {
        var params = getParameters();

        var xmlReportLocation = params.getXmlReportLocation().getAsFile().getOrNull();
        if (xmlReportLocation != null) {
            tryToDeleteRecursively(xmlReportLocation.toPath());
        }

        var htmlReportLocation = params.getHtmlReportLocation().getAsFile().getOrNull();
        if (htmlReportLocation != null) {
            tryToDeleteRecursively(htmlReportLocation.toPath());
        }

        var serviceParams = SonarLintServiceAnalysisParams.builder()
            .pluginPaths(params.getPluginFiles().getFiles().stream()
                .map(File::toPath)
                .collect(toUnmodifiableList())
            )
            .languagesToProcess(params.getLanguagesToProcess().get())
            .repositoryRoot(params.getRootDirectory().get().getAsFile().toPath())
            .sonarUserHome(createDirectories(params.getHomeDirectory().get().getAsFile().toPath()))
            .workDir(createDirectories(params.getWorkDirectory().get().getAsFile().toPath()))
            .build();

        var enabledRules = params.getEnabledRules().get();
        var disabledRules = new LinkedHashSet<>(params.getDisabledRules().get());
        params.getAutomaticallyDisabledRules().get().keySet().stream()
            .filter(not(enabledRules::contains))
            .forEach(disabledRules::add);

        try (var service = new SonarLintServiceAnalysis(serviceParams)) {
            var issues = service.analyze(
                params.getSourceFiles().get(),
                params.getSonarProperties().get(),
                enabledRules,
                disabledRules,
                params.getRulesProperties().get()
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
