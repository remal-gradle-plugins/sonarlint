package name.remal.gradle_plugins.sonarlint;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.errorprone.annotations.ForOverride;
import java.io.File;
import java.util.List;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceHelp;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceHelpParams;

abstract class AbstractSonarLintHelpWorkAction
    implements AbstractSonarLintWorkAction<SonarLintHelpWorkActionParams> {

    @ForOverride
    abstract void executeImpl(SonarLintServiceHelp service);

    @Override
    public final void execute() {
        var params = getParameters();

        var serviceParams = SonarLintServiceHelpParams.builder()
            .pluginPaths(params.getPluginFiles().getFiles().stream()
                .map(File::toPath)
                .collect(toUnmodifiableList())
            )
            .languagesToProcess(List.of(SonarLintLanguage.values()))
            .build();

        try (var service = new SonarLintServiceHelp(serviceParams)) {
            executeImpl(service);
        }
    }

}
