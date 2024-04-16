package name.remal.gradle_plugins.sonarlint;

import lombok.Getter;
import lombok.Setter;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;

@Getter
@Setter
public abstract class SonarLintLoggingOptions {

    @Console
    public abstract Property<Boolean> getWithDescription();

    {
        getWithDescription().convention(true);
    }

}
