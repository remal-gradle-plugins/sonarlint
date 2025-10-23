package name.remal.gradle_plugins.sonarlint.internal.client;

import java.io.File;
import java.util.Optional;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintParams;
import org.immutables.value.Value;

@Value.Immutable
@SuppressWarnings("immutables:subtype")
public interface SonarLintClientParams extends SonarLintParams {

    int getJavaMajorVersion();

    String getJavaRuntimeVersion();

    File getJavaExecutable();

    Set<File> getCoreClasspath();

    Optional<String> getMaxHeapSize();

}
