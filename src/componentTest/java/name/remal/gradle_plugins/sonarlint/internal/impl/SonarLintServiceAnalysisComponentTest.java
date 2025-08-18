package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.nio.file.Files.createTempDirectory;
import static name.remal.gradle_plugins.sonarlint.RuleExamples.writeSonarRuleExample;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyProxy;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.RuleExamples.ConfiguredSonarExampleRulesWithDistinctLanguageProvider;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("java:S2187")
class SonarLintServiceAnalysisComponentTest extends AbstractSonarLintServiceComponentTest {

    private static final Path tempPath = asLazyProxy(Path.class, () -> createTempDirectory("sonarlint-test-"));

    private static SonarLintServiceAnalysis service;

    @BeforeAll
    static void beforeAll() {
        var params = configureParamsBuilderBase(SonarLintServiceAnalysisParams.builder())
            .sonarUserHome(tempPath.resolve("sonar-user").toFile())
            .workDir(tempPath.resolve("sonar-work").toFile())
            .build();

        service = new SonarLintServiceAnalysis(params);
    }

    @AfterAll
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void afterAll() {
        service.close();
        tryToDeleteRecursively(tempPath);
    }


    @ParameterizedTest
    @ArgumentsSource(ConfiguredSonarExampleRulesWithDistinctLanguageProvider.class)
    void analyze(
        String rule,
        @TempDir(cleanup = ON_SUCCESS) File projectDir
    ) {
        var relativeFilePath = writeSonarRuleExample(projectDir, rule);
        var sourceFiles = List.of(SourceFile.builder()
            .file(new File(projectDir, relativeFilePath))
            .relativePath(relativeFilePath)
            .build()
        );
        var issues = service.analyze(
            projectDir.toPath(),
            sourceFiles,
            Map.of(),
            false,
            Set.of(rule),
            Set.of(),
            Map.of()
        );
        assertThat(issues)
            .extracting("rule")
            .contains(rule);
    }


    final Set<SonarLintLanguage> languagesWithoutRules = Set.of(
        SonarLintLanguage.JSON,
        SonarLintLanguage.JSP,
        SonarLintLanguage.YAML
    );

    @ParameterizedTest
    @EnumSource(SonarLintLanguage.class)
    void enabledRulesForSonarLintLanguage(SonarLintLanguage language) {
        var enabledRules = service.getEnabledRules(
            Set.of(language),
            true,
            Set.of(),
            Set.of()
        );

        if (languagesWithoutRules.contains(language)) {
            assertThat(enabledRules).as("%s: enabled rules (expect none)", language)
                .isEmpty();

        } else {
            assertThat(enabledRules).as("%s: enabled rules", language)
                .isNotEmpty();
        }
    }

}
