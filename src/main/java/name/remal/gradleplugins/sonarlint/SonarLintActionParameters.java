package name.remal.gradleplugins.sonarlint;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.workers.WorkParameters;

interface SonarLintActionParameters extends WorkParameters {

    RegularFileProperty getRunnerParamsFile();

}
