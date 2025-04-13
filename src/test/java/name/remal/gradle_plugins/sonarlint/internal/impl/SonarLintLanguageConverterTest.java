package name.remal.gradle_plugins.sonarlint.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.LinkedHashSet;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.toolkit.testkit.MinSupportedJavaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

@MinSupportedJavaVersion(17)
class SonarLintLanguageConverterTest {

    @ParameterizedTest
    @EnumSource(SonarLintLanguage.class)
    void allSonarLintLanguagesConverted(SonarLintLanguage language) {
        assertDoesNotThrow(() -> SonarLintLanguageConverter.convertSonarLintLanguage(language));
    }

    @Test
    void allLanguagesAreConvertedOneToOne() {
        var convertedSonarLanguages = new LinkedHashSet<SonarLanguage>();
        for (var language : SonarLintLanguage.values()) {
            var sonarLanguage = SonarLintLanguageConverter.convertSonarLintLanguage(language);
            convertedSonarLanguages.add(sonarLanguage);
        }

        assertThat(convertedSonarLanguages)
            .hasSameSizeAs(SonarLintLanguage.values());
    }

}
