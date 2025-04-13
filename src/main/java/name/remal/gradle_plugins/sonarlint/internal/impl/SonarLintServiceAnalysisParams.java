package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.nio.file.Path;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
public class SonarLintServiceAnalysisParams extends AbstractSonarLintServiceParams {

    @NonNull
    Path repositoryRoot;

    @NonNull
    Path sonarUserHome;

    @NonNull
    Path workDir;

}
