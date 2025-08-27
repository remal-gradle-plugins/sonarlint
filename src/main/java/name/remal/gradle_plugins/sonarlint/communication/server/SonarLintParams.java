package name.remal.gradle_plugins.sonarlint.communication.server;

import java.io.File;
import java.io.Serializable;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.immutables.value.Value;

@Value.Immutable
public interface SonarLintParams extends Serializable {

    Set<File> getPluginFiles();

    Set<SonarLintLanguage> getEnabledPluginLanguages();

}
