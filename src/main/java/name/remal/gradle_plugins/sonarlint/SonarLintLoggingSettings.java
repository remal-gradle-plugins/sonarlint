package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;

public abstract class SonarLintLoggingSettings {

    @Console
    public abstract Property<Boolean> getWithDescription();

    {
        getWithDescription().convention(true);
    }

}
