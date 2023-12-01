package name.remal.gradle_plugins.sonarlint;

import lombok.Getter;
import lombok.Setter;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

@Getter
@Setter
public abstract class SonarLintLoggingOptions {

    @Internal
    public abstract Property<Boolean> getWithDescription();

    {
        getWithDescription().convention(true);
    }

}
