package name.remal.gradle_plugins.sonarlint.internal.impl;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

@NoArgsConstructor(access = PRIVATE)
abstract class SonarLintLanguageConverter {

    public static SonarLanguage convertSonarLintLanguage(SonarLintLanguage language) {
        for (var sonarLang : SonarLanguage.values()) {
            if (sonarLang.name().equals(language.name())) {
                return sonarLang;
            }
        }

        throw new IllegalArgumentException("Unsupported SonarLint language: " + language);
    }

}
