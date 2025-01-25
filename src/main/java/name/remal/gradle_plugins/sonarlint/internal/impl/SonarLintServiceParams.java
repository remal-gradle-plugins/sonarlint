package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.nio.file.Path;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import org.jetbrains.annotations.Unmodifiable;

@SuperBuilder
abstract class SonarLintServiceParams {

    @NonNull
    protected final Path workDir;

    @Unmodifiable
    @Singular("pluginsClasspathElement")
    protected final Set<Path> pluginsClasspath;

    @Unmodifiable
    @Singular
    protected final Set<String> includedLanguages;

    @Unmodifiable
    @Singular
    protected final Set<String> excludedLanguages;

    @Nullable
    @org.jetbrains.annotations.Nullable
    protected final NodeJsFound nodeJsInfo;

}
