package name.remal.gradle_plugins.sonarlint.internal.client;

import java.io.File;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface JavaExecParams {

    File getExecutable();

    List<File> getClasspath();

    String getMainClass();

    Optional<String> getMaxHeapSize();

    List<String> getArguments();

}
