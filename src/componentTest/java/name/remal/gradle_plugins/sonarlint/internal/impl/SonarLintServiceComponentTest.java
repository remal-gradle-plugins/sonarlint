package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.RuleExamples.getConfiguredSonarExampleRules;
import static name.remal.gradle_plugins.sonarlint.RuleExamples.writeSonarRuleExample;
import static name.remal.gradle_plugins.sonarlint.internal.impl.LogOutputViaSlf4j.LOG_OUTPUT_VIA_SLF4J;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarPropertiesInfo.KNOWN_SONAR_PROPERTIES;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarPropertiesInfo.UNKNOWN_SONAR_PROPERTIES;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathFirstLevelLibraryNotations;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathLibraryFilePaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import name.remal.gradle_plugins.sonarlint.RuleExamples;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

@Execution(CONCURRENT)
class SonarLintServiceComponentTest {

    static final SonarLintService service = SonarLintService.builder()
            .pluginFiles(getPluginFiles())
            .build();

    @BeforeEach
    void beforeEach() {
        // Fix Sonar logging for parallel tests - every thread should have a Sonar logger target defined
        SonarLintLogger.get().setTarget(LOG_OUTPUT_VIA_SLF4J);
    }

    @AfterAll
    static void afterAll() {
        service.close();
    }


    //#region Analyze

    @ParameterizedTest
    @MethodSource("rulesToAnalyze")
    void analyze(
            String rule,
            @TempDir(cleanup = ON_SUCCESS) File projectDir
    ) {
        if (!rule.startsWith("java:")) {
            return;
        }

        var relativeFilePath = writeSonarRuleExample(projectDir, rule);
        var sourceFiles = List.of(SourceFile.builder()
                .file(new File(projectDir, relativeFilePath))
                .relativePath(relativeFilePath)
                .build()
        );
        var issues = service.analyze(
                rule,
                projectDir,
                sourceFiles,
                Map.of(),
                Set.of(SonarLintLanguage.values()),
                Set.of(rule),
                Set.of(),
                Map.of(),
                null
        );
        assertThat(issues)
                .extracting("rule")
                .contains(rule);
    }

    private static Stream<String> rulesToAnalyze() {
        return getConfiguredSonarExampleRules().stream()
                .collect(groupingBy(
                        RuleExamples::getSonarRuleLanguage,
                        LinkedHashMap::new,
                        mapping(identity(), toList())
                ))
                .values()
                .stream()
                .map(list -> list.get(0));
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

    //#endregion


    //#region Properties documentations

    @Test
    void sonarLintLanguagesUseKnownProperties() {
        var knownProperties = service.collectPropertiesDocumentation(null).getProperties().keySet();
        for (var lang : SonarLintLanguage.values()) {
            var fileSuffixesPropKey = lang.getFileSuffixesPropKey();
            if (fileSuffixesPropKey != null) {
                assertTrue(
                        knownProperties.contains(fileSuffixesPropKey),
                        lang + ": unknown fileSuffixesPropKey: " + fileSuffixesPropKey
                );
            }

            var filenamePatternsPropKey = lang.getFilenamePatternsPropKey();
            if (filenamePatternsPropKey != null) {
                assertTrue(
                        knownProperties.contains(filenamePatternsPropKey),
                        lang + ": unknown filenamePatternsPropKey: " + filenamePatternsPropKey
                );
            }
        }
    }

    @Test
    @SuppressWarnings({"java:S3415", "java:S5841"})
    void knownSonarPropertiesAreStillUnknown() {
        var knownProperties = service.collectPropertiesDocumentationWithoutEnrichment().getProperties().keySet();
        assertThat(KNOWN_SONAR_PROPERTIES)
                .doesNotContainAnyElementsOf(knownProperties);
    }

    @Test
    @SuppressWarnings({"java:S3415", "java:S5841"})
    void unknownSonarPropertiesAreStillUnknown() {
        var knownProperties = service.collectPropertiesDocumentationWithoutEnrichment().getProperties().keySet();
        assertThat(UNKNOWN_SONAR_PROPERTIES)
                .doesNotContainAnyElementsOf(knownProperties);
    }

    //#endregion


    //#region Rules documentations

    @Test
    void knownRulesArePresent() {
        var rulesDoc = service.collectRulesDocumentation(null);

        var knownRules = new LinkedHashMap<String, String>();
        knownRules.put("java:S106", "Standard outputs should not be used directly to log anything");
        knownRules.forEach((key, rule) -> {
            var ruleDoc = rulesDoc.getRules().get(key);
            assertNotNull(ruleDoc, "Rule not documented: " + key);
            assertEquals(rule, ruleDoc.getName(), key + ": rule name");
        });
    }

    //#endregion


    private static Set<File> getPluginFiles() {
        var scope = "sonar-plugins";
        var notations = getTestClasspathFirstLevelLibraryNotations(scope);
        return notations.stream()
                .map(notation -> getTestClasspathLibraryFilePaths(scope, notation))
                .flatMap(Collection::stream)
                .map(Path::toFile)
                .collect(toImmutableSet());
    }

}
