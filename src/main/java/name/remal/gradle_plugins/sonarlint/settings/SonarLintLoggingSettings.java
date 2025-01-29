package name.remal.gradle_plugins.sonarlint.settings;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;

public abstract class SonarLintLoggingSettings {

    @Console
    public abstract Property<Boolean> getWithDescription();

    {
        getWithDescription().convention(true);
    }


    @Console
    public abstract Property<Boolean> getHideWarnings();

    {
        getHideWarnings().convention(false);
    }

}
