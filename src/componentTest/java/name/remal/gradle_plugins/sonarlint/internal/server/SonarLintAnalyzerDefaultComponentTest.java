package name.remal.gradle_plugins.sonarlint.internal.server;

import static name.remal.gradle_plugins.sonarlint.RuleExamples.writeSonarRuleExample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS;

import java.io.File;
import java.util.List;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.RuleExamples.ConfiguredSonarExampleRulesWithDistinctLanguageProvider;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.sonarlint.internal.server.api.ImmutableSonarLintAnalyzeParams;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

class SonarLintAnalyzerDefaultComponentTest extends AbstractSonarLintComponentTest<SonarLintAnalyzerDefault> {

    @Override
    protected SonarLintAnalyzerDefault createInstance(SonarLintSharedCode shared) {
        return new SonarLintAnalyzerDefault(shared);
    }


    @ParameterizedTest
    @ArgumentsSource(ConfiguredSonarExampleRulesWithDistinctLanguageProvider.class)
    void analyze(
        String rule,
        @TempDir(cleanup = ON_SUCCESS) File projectDir,
        TestInfo testInfo
    ) throws Exception {
        var relativeFilePath = writeSonarRuleExample(projectDir, rule);
        var sourceFiles = List.of(SourceFile.builder()
            .file(new File(projectDir, relativeFilePath))
            .relativePath(relativeFilePath)
            .build()
        );
        var issues = instance.analyze(
            ImmutableSonarLintAnalyzeParams.builder()
                .repositoryRoot(projectDir)
                .moduleId(testInfo.getDisplayName())
                .sourceFiles(sourceFiles)
                .enableRulesActivatedByDefault(false)
                .enabledRulesConfig(Set.of(rule))
                .build(),
            null
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
        var enabledRules = instance.getEnabledRules(
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
