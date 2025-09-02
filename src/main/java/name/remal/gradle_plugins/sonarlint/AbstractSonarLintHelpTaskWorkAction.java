package name.remal.gradle_plugins.sonarlint;

import com.google.errorprone.annotations.ForOverride;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.server.ImmutableSonarLintParams;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintHelpDefault;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintSharedCode;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintHelp;

abstract class AbstractSonarLintHelpTaskWorkAction
    implements AbstractSonarLintTaskWorkAction<SonarLintHelpWorkActionParams> {

    @ForOverride
    abstract void executeImpl(SonarLintHelp service) throws Throwable;

    @Override
    @SneakyThrows
    public final void execute() {
        var params = getParameters();

        var sonarLintParams = ImmutableSonarLintParams.builder()
            .pluginFiles(params.getPluginFiles())
            .enabledPluginLanguages(params.getLanguagesToProcess().get())
            .build();
        try (var shared = new SonarLintSharedCode(sonarLintParams)) {
            var service = new SonarLintHelpDefault(shared);
            executeImpl(service);
        }
    }

}
