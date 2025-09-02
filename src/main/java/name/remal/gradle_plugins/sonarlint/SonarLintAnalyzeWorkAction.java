package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PUBLIC;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursivelyIgnoringFailure;
import static name.remal.gradle_plugins.toolkit.VerificationExceptionUtils.newVerificationException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.server.ImmutableSonarLintParams;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintAnalyzerDefault;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintParams;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintSharedCode;
import name.remal.gradle_plugins.sonarlint.internal.server.api.ImmutableSonarLintAnalyzeParams;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintLogSink;
import name.remal.gradle_plugins.toolkit.CloseablesContainer;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleHtmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesRenderer;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.issues.TextIssuesRenderer;
import org.jspecify.annotations.Nullable;

@CustomLog
@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class SonarLintAnalyzeWorkAction
    implements AbstractSonarLintTaskWorkAction<SonarLintAnalyzeWorkActionParams> {

    @Override
    public void execute() {
        var params = getParameters();
        SonarLintAnalyzerFactory analyzerFactory = (sonarLintParams, closeables) -> {
            var shared = closeables.registerCloseable(
                new SonarLintSharedCode(sonarLintParams)
            );
            return new SonarLintAnalyzerDefault(shared);
        };
        executeForParams(params, analyzerFactory, null);
    }


    @FunctionalInterface
    public interface SonarLintAnalyzerFactory {
        SonarLintAnalyzer getAnalyzer(SonarLintParams sonarLintParams, CloseablesContainer closeables);
    }

    @SneakyThrows
    public static void executeForParams(
        SonarLintAnalyzeWorkActionParams params,
        SonarLintAnalyzerFactory analyzerFactory,
        @Nullable Supplier<SonarLintLogSink> logSinkSupplier
    ) {
        var xmlReportLocation = params.getXmlReportLocation().getAsFile().getOrNull();
        if (xmlReportLocation != null) {
            tryToDeleteRecursivelyIgnoringFailure(xmlReportLocation.toPath());
        }

        var htmlReportLocation = params.getHtmlReportLocation().getAsFile().getOrNull();
        if (htmlReportLocation != null) {
            tryToDeleteRecursivelyIgnoringFailure(htmlReportLocation.toPath());
        }

        var enabledRules = params.getEnabledRules().get();
        var disabledRules = new LinkedHashSet<>(params.getDisabledRules().get());
        params.getAutomaticallyDisabledRules().get().keySet().stream()
            .filter(not(enabledRules::contains))
            .forEach(disabledRules::add);

        final Collection<Issue> issues;
        var sourceFiles = params.getSourceFiles().get();
        if (sourceFiles.isEmpty()) {
            issues = List.of();

        } else {
            try (var closeables = new CloseablesContainer()) {
                var sonarLintParams = ImmutableSonarLintParams.builder()
                    .pluginFiles(params.getPluginFiles())
                    .build();
                var analyzer = analyzerFactory.getAnalyzer(sonarLintParams, closeables);

                var analyzeParams = ImmutableSonarLintAnalyzeParams.builder()
                    .repositoryRoot(params.getRootDirectory().get().getAsFile())
                    .moduleId(params.getModuleId().get())
                    .sourceFiles(sourceFiles)
                    .enabledLanguages(params.getLanguagesToProcess().get())
                    .sonarProperties(params.getSonarProperties().get())
                    .enabledRulesConfig(enabledRules)
                    .disabledRulesConfig(disabledRules)
                    .rulesPropertiesConfig(params.getRulesProperties().get())
                    .build();
                var logSink = logSinkSupplier != null ? logSinkSupplier.get() : null;
                issues = analyzer.analyze(analyzeParams, logSink);
            }
        }

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
