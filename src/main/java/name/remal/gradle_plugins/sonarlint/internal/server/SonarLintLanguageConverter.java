package name.remal.gradle_plugins.sonarlint.internal.server;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

@NoArgsConstructor(access = PRIVATE)
abstract class SonarLintLanguageConverter {

    public static SonarLanguage convertSonarLintLanguage(String languageName) {
        for (var sonarLang : SonarLanguage.values()) {
            if (sonarLang.name().equalsIgnoreCase(languageName)) {
                return sonarLang;
            }
        }

        throw new IllegalArgumentException("Unsupported SonarLint language: `" + languageName + "`");
    }

    public static SonarLanguage convertSonarLintLanguage(SonarLintLanguage language) {
        return convertSonarLintLanguage(language.name());
    }

}
