package name.remal.gradle_plugins.sonarlint.internal.server;

import java.io.File;
import java.io.Serializable;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.immutables.value.Value;

@Value.Immutable
public interface SonarLintParams extends Serializable {

    Set<File> getPluginFiles();

    @Value.Default
    default Set<SonarLintLanguage> getEnabledPluginLanguages() {
        return Set.of(SonarLintLanguage.values());
    }

}
