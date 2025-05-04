package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.io.File;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
public class SonarLintServiceAnalysisParams extends AbstractSonarLintServiceParams {

    @NonNull
    File sonarUserHome;

    @NonNull
    File workDir;

}
