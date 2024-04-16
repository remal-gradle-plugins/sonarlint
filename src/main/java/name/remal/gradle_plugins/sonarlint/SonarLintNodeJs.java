package name.remal.gradle_plugins.sonarlint;

import lombok.Getter;
import lombok.Setter;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;

@Getter
@Setter
public abstract class SonarLintNodeJs {

    @Internal
    public abstract RegularFileProperty getNodeJsExecutable();

    @Internal
    public abstract Property<Boolean> getDetectNodeJs();

    {
        getDetectNodeJs().convention(false);
    }

    @Console
    public abstract Property<Boolean> getLogNodeJsNotFound();

    {
        getLogNodeJsNotFound().convention(true);
    }

}
