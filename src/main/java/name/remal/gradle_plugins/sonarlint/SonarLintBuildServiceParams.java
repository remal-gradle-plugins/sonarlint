package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.services.BuildServiceParameters;

interface SonarLintBuildServiceParams extends BuildServiceParameters {

    ConfigurableFileCollection getCoreClasspath();

    ConfigurableFileCollection getLoggingClasspath();

    ConfigurableFileCollection getPluginFiles();

}
