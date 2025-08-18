package name.remal.gradle_plugins.sonarlint.internal.impl;

import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarPropertiesInfo.KNOWN_SONAR_PROPERTIES;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarPropertiesInfo.UNKNOWN_SONAR_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

import name.remal.gradle_plugins.sonarlint.RuleExamples.ConfiguredSonarExampleRulesProvider;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

class SonarLintServiceHelpComponentTest extends AbstractSonarLintServiceComponentTest {

    private static SonarLintServiceHelp service;

    @BeforeAll
    static void beforeAll() {
        var params = configureParamsBuilderBase(SonarLintServiceHelpParams.builder())
            .build();

        service = new SonarLintServiceHelp(params);
    }

    @AfterAll
    static void afterAll() {
        service.close();
    }


    @Nested
    class PropertiesDocumentation {

        @Test
        void sonarLintLanguagesUseKnownProperties() {
            var knownProperties = service.collectPropertiesDocumentation().getProperties().keySet();
            assertThat(knownProperties).as("Known properties")
                .isNotEmpty();

            try (var assertions = new AutoCloseableSoftAssertions()) {
                for (var lang : SonarLintLanguage.values()) {
                    var fileSuffixesPropKey = lang.getFileSuffixesPropKey();
                    if (fileSuffixesPropKey != null) {
                        assertions.assertThat(knownProperties.contains(fileSuffixesPropKey))
                            .as("%s: unknown fileSuffixesPropKey: %s", lang, fileSuffixesPropKey)
                            .isTrue();
                    }

                    var filenamePatternsPropKey = lang.getFilenamePatternsPropKey();
                    if (filenamePatternsPropKey != null) {
                        assertions.assertThat(knownProperties.contains(filenamePatternsPropKey))
                            .as("%s: unknown filenamePatternsPropKey: %s", lang, fileSuffixesPropKey)
                            .isTrue();
                    }
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

    }


    @Nested
    class RulesDocumentation {

        @ParameterizedTest
        @ArgumentsSource(ConfiguredSonarExampleRulesProvider.class)
        void knownRulesAreDocumented(String rule) {
            var rulesDoc = service.collectRulesDocumentation();
            assertThat(rulesDoc.getRules())
                .extractingByKey(rule).as("%s documentation", rule)
                .isNotNull();
        }

    }

}
