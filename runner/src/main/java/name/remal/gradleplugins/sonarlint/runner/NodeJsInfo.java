package name.remal.gradleplugins.sonarlint.runner;

import static com.google.common.io.ByteStreams.toByteArray;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.CustomLog;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import name.remal.gradleplugins.sonarlint.shared.RunnerParams;
import name.remal.gradleplugins.toolkit.ObjectUtils;
import name.remal.gradleplugins.toolkit.PathUtils;

@Value
@Builder
@CustomLog
public class NodeJsInfo {

    @SneakyThrows
    public static NodeJsInfo collectNodeJsInfoFor(RunnerParams params) {
        val predefinedVersion = Optional.ofNullable(params.getSonarProperties().get(
                "sonar.nodejs.version"
            ))
            .filter(ObjectUtils::isNotEmpty)
            .orElse(params.getDefaultNodeJsVersion());

        val nodeJsPath = Optional.ofNullable(params.getSonarProperties().get(
                "sonar.nodejs.executable"
            ))
            .filter(ObjectUtils::isNotEmpty)
            .map(Paths::get)
            .map(PathUtils::normalizePath)
            .orElse(null);
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

            val bytes = toByteArray(process.getInputStream());
            String content = new String(bytes, UTF_8).trim();
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
