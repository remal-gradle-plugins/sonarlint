package name.remal.gradle_plugins.sonarlint;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.errorprone.annotations.ForOverride;
import java.io.File;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceHelpParams;

abstract class AbstractSonarLintHelpWorkAction
    implements AbstractSonarLintWorkAction<AbstractSonarLintWorkActionParams> {

    @ForOverride
    protected abstract void executeImpl(SonarLintServiceHelpParams serviceParams);

    @Override
    public final void execute() {
        var serviceParams = SonarLintServiceHelpParams.builder()
            .pluginPaths(getParameters().getPluginFiles().getFiles().stream()
                .map(File::toPath)
                .collect(toUnmodifiableList())
            )
            .build();
        executeImpl(serviceParams);
    }

}
