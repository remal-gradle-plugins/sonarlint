package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.toolkit.internal.Flags.isInFunctionTest;

import lombok.Getter;
import lombok.Setter;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

@Getter
@Setter
public abstract class SonarLintForkOptions {

    static final boolean IS_FORK_ENABLED_DEFAULT = !isInFunctionTest();


    @Internal
    public abstract Property<Boolean> getEnabled();

    @Internal
    public abstract Property<String> getMaxHeapSize();

    {
        getEnabled().convention(IS_FORK_ENABLED_DEFAULT);
    }

}
