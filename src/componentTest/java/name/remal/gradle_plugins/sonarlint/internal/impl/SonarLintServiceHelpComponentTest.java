package name.remal.gradle_plugins.sonarlint.internal.impl;

import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarPropertiesInfo.KNOWN_SONAR_PROPERTIES;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarPropertiesInfo.UNKNOWN_SONAR_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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


    @Test
    void sonarLintLanguagesUseKnownProperties() {
        var knownProperties = service.collectPropertiesDocumentation().getProperties().keySet();
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

}
