package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.workers.WorkParameters;

interface AbstractSonarLintWorkActionParams extends WorkParameters {

    ConfigurableFileCollection getPluginFiles();

}
