package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound.nodeJsNotFound;

import com.google.auto.service.AutoService;
import java.io.File;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsInfo;
import org.gradle.api.provider.ProviderFactory;

@AutoService(NodeJsExecutableMethods.class)
class NodeJsExecutableMethodsDefault extends NodeJsExecutableMethods {

    @Override
    public NodeJsInfo getNodeJsInfo(ProviderFactory providers, File file) {
        setExecutePermissionsIfNeeded(file);

        var command = new String[]{file.getAbsolutePath(), "--version"};
        var execResult = providers.exec(spec -> {
            spec.setCommandLine((Object[]) command);
            spec.setIgnoreExitValue(true);
        });

        var exitCode = execResult.getResult().get().getExitValue();
        if (exitCode != 0) {
            var errorBytes = execResult.getStandardError().getAsBytes().get();
            var errorOutput = new String(errorBytes, UTF_8);
            return nodeJsNotFound(format(
                "%s returned %d exit code with error output:%n%s",
                join(" ", command),
                exitCode,
                errorOutput
            ));
        }

        var bytes = execResult.getStandardOutput().getAsBytes().get();
        var output = new String(bytes, UTF_8);
        var version = parseVersion(output);
        if (version == null) {
            return nodeJsNotFound(format(
                "%s produced not a Node.js version output:%n%s",
                join(" ", command),
                output
            ));

        }

        return NodeJsFound.builder()
            .executable(file)
            .version(version)
            .build();
    }

}
