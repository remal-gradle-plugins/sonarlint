package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound.nodeJsNotFound;

import com.google.auto.service.AutoService;
import java.io.File;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsInfo;
import org.gradle.api.provider.ProviderFactory;

@AutoService(NodeJsExecutableMethods.class)
class NodeJsExecutableMethodsDefault extends NodeJsExecutableMethods {

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public NodeJsInfo getNodeJsInfo(ProviderFactory providers, File file) {
        setExecutePermissionsIfNeeded(file);

        val command = new String[]{file.getAbsolutePath(), "--version"};
        val execResult = providers.exec(spec -> {
            spec.setCommandLine((Object[]) command);
            spec.setIgnoreExitValue(true);
        });

        val exitCode = execResult.getResult().get().getExitValue();
        if (exitCode != 0) {
            val errorBytes = execResult.getStandardError().getAsBytes().get();
            val errorOutput = new String(errorBytes, UTF_8);
            return nodeJsNotFound(format(
                "%s returned %d exit code with error output:%n%s",
                join(" ", command),
                exitCode,
                errorOutput
            ));
        }

        val bytes = execResult.getStandardOutput().getAsBytes().get();
        val output = new String(bytes, UTF_8);
        val version = parseVersion(output);
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
