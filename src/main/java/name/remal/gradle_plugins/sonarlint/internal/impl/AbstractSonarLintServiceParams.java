package name.remal.gradle_plugins.sonarlint.internal.impl;

import static lombok.AccessLevel.PRIVATE;

import java.nio.file.Path;
import java.util.Set;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.jetbrains.annotations.Unmodifiable;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@SuperBuilder
public abstract class AbstractSonarLintServiceParams {

    @Unmodifiable
    @Singular
    Set<Path> pluginPaths;


    @Unmodifiable
    @Singular("languageToProcess")
    Set<SonarLintLanguage> languagesToProcess;

}
