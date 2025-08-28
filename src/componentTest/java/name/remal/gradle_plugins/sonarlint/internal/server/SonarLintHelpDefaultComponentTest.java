package name.remal.gradle_plugins.sonarlint.internal.server;

import static name.remal.gradle_plugins.sonarlint.SonarPropertiesInfo.KNOWN_SONAR_PROPERTIES;
import static name.remal.gradle_plugins.sonarlint.SonarPropertiesInfo.UNKNOWN_SONAR_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

import name.remal.gradle_plugins.sonarlint.RuleExamples.ConfiguredSonarExampleRulesProvider;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

class SonarLintHelpDefaultComponentTest extends AbstractSonarLintComponentTest<SonarLintHelpDefault> {

    @Override
    protected SonarLintHelpDefault createInstance(SonarLintSharedCode shared) {
        return new SonarLintHelpDefault(shared);
    }


    @Nested
    class PropertiesDocumentation {

        @Test
        void sonarLintLanguagesUseKnownProperties() throws Exception {
            var knownProperties = instance.getPropertiesDocumentation().getProperties().keySet();
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
            var knownProperties = instance.getPropertiesDocumentationWithoutEnrichment().getProperties().keySet();
            assertThat(KNOWN_SONAR_PROPERTIES)
                .doesNotContainAnyElementsOf(knownProperties);
        }

        @Test
        @SuppressWarnings({"java:S3415", "java:S5841"})
        void unknownSonarPropertiesAreStillUnknown() {
            var knownProperties = instance.getPropertiesDocumentationWithoutEnrichment().getProperties().keySet();
            assertThat(UNKNOWN_SONAR_PROPERTIES)
                .doesNotContainAnyElementsOf(knownProperties);
        }

    }


    @Nested
    class RulesDocumentation {

        @ParameterizedTest
        @ArgumentsSource(ConfiguredSonarExampleRulesProvider.class)
        void knownRulesAreDocumented(String rule) throws Exception {
            var rulesDoc = instance.getRulesDocumentation();
            assertThat(rulesDoc.getRules())
                .extractingByKey(rule).as("%s documentation", rule)
                .isNotNull();
        }

    }

}
