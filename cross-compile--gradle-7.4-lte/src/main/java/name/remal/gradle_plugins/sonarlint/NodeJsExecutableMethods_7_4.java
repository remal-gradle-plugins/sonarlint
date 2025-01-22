package name.remal.gradle_plugins.sonarlint;

import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound.nodeJsNotFound;

import com.google.auto.service.AutoService;
import java.io.File;
import lombok.CustomLog;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsInfo;
import org.gradle.api.provider.ProviderFactory;

@AutoService(NodeJsExecutableMethods.class)
@CustomLog
class NodeJsExecutableMethods_7_4 extends NodeJsExecutableMethods {

    @Override
    @SneakyThrows
    public NodeJsInfo getNodeJsInfo(ProviderFactory providers, File file) {
        setExecutePermissionsIfNeeded(file);

        var command = new String[]{file.getAbsolutePath(), "--version"};
        var process = getRuntime().exec(command);

        var exitCode = process.waitFor();
        if (exitCode != 0) {
            try (var errorStream = process.getErrorStream()) {
                var errorBytes = errorStream.readAllBytes();
                var errorOutput = new String(errorBytes, UTF_8);
                return nodeJsNotFound(format(
                    "%s returned %d exit code with error output:%n%s",
                    join(" ", command),
                    exitCode,
                    errorOutput
                ));
            }
        }

        try (var inputStream = process.getInputStream()) {
            var bytes = inputStream.readAllBytes();
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


}
