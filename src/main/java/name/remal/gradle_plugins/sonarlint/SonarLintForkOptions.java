package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

public abstract class SonarLintForkOptions {

    static final boolean IS_FORK_ENABLED_DEFAULT = true;


    @Internal
    public abstract Property<Boolean> getEnabled();

    @Internal
    public abstract Property<String> getMaxHeapSize();

    {
        getEnabled().convention(IS_FORK_ENABLED_DEFAULT);
    }

}
