package name.remal.gradleplugins.sonarlint;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

public abstract class SonarLintForkOptions {

    @Internal
    public abstract Property<Boolean> getEnabled();

    @Internal
    public abstract Property<String> getMaxHeapSize();

    {
        getEnabled().convention(true);
    }

}
