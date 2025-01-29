package name.remal.gradle_plugins.sonarlint;

import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.File;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceAnalysis;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceAnalysisParams;

abstract class SonarLintWorkAction implements AbstractSonarLintWorkAction<SonarLintWorkActionParams> {

    @Override
    public void execute() {
        var serviceParams = SonarLintServiceAnalysisParams.builder()
            .pluginPaths(getParameters().getPluginFiles().getFiles().stream()
                .map(File::toPath)
                .collect(toUnmodifiableList())
            )
            .build();
        try (var service = new SonarLintServiceAnalysis(serviceParams)) {

        }
    }

}
