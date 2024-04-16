package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.service.AutoService;
import java.io.File;
import lombok.val;
import org.gradle.api.provider.ProviderFactory;

@AutoService(NodeJsExecutableMethods.class)
class NodeJsExecutableMethodsDefault implements NodeJsExecutableMethods {

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public NodeJsVersionResult getNodeJsVersion(ProviderFactory providers, File file) {
        val command = new String[]{file.getAbsolutePath(), "--version"};
        val execResult = providers.exec(spec -> {
            spec.setCommandLine((Object[]) command);
            spec.setIgnoreExitValue(true);
        });

        val exitCode = execResult.getResult().get().getExitValue();
        if (exitCode != 0) {
            val errorBytes = execResult.getStandardError().getAsBytes().get();
            val errorOutput = new String(errorBytes, UTF_8);
            return NodeJsVersionResult.error(format(
                "%s returned %d exit code. Error output:%n%s",
                join(" ", command),
                exitCode,
                errorOutput
            ));
        }

        val bytes = execResult.getStandardOutput().getAsBytes().get();
        val result = new String(bytes, UTF_8);
        return NodeJsVersionResult.of(result);
    }

}
