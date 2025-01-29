package name.remal.gradle_plugins.sonarlint.settings;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;

public interface SonarLintNodeJsSettings {

    @InputFile
    @org.gradle.api.tasks.Optional
    RegularFileProperty getNodeJsExecutable();

}
