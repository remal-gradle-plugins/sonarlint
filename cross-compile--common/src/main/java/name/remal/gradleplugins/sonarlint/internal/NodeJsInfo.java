package name.remal.gradleplugins.sonarlint.internal;

import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;
import static name.remal.gradle_plugins.toolkit.InputOutputStreamUtils.readStringFromStream;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.CustomLog;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import name.remal.gradle_plugins.toolkit.PathUtils;

@Value
@Builder
@CustomLog
public class NodeJsInfo {

    @SneakyThrows
    public static NodeJsInfo collectNodeJsInfoFor(SonarLintExecutionParams params) {
        String predefinedVersion = params.getSonarProperties().getting("sonar.nodejs.version").getOrNull();
        if (isEmpty(predefinedVersion)) {
            predefinedVersion = params.getDefaultNodeJsVersion().get();
        }

        val nodeJsPath = params.getSonarProperties().getting("sonar.nodejs.executable")
            .map(Paths::get)
            .map(PathUtils::normalizePath)
            .getOrNull();
        if (nodeJsPath != null) {
            val command = new String[]{nodeJsPath.toString(), "-v"};
            val process = getRuntime().exec(command);
            if (!process.waitFor(1, MINUTES)) {
                process.destroy();
                throw new AssertionError(format(
                    "Command execution timeout: %s",
                    join(" ", command)
                ));
            }
            if (process.exitValue() != 0) {
                throw new AssertionError(format(
                    "Command returned %d: %s",
                    process.exitValue(),
                    join(" ", command)
                ));
            }

            String content = readStringFromStream(process.getInputStream(), UTF_8).trim();
            while (content.startsWith("v")) {
                content = content.substring(1);
            }

            return NodeJsInfo.builder()
                .nodeJsPath(nodeJsPath)
                .version(content.isEmpty() ? predefinedVersion : content)
                .build();
        }


        return NodeJsInfo.builder()
            .version(predefinedVersion)
            .build();
    }


    @Nullable
    Path nodeJsPath;

    @Nullable
    String version;

}
