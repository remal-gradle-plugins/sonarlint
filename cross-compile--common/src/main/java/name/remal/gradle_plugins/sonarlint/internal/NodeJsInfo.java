package name.remal.gradle_plugins.sonarlint.internal;

import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.file.Files.getPosixFilePermissions;
import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static name.remal.gradle_plugins.toolkit.InputOutputStreamUtils.readStringFromStream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
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

        val nodeJsPath = params.getSonarProperties().getting("sonar.nodejs.executable")
            .map(Paths::get)
            .map(PathUtils::normalizePath)
            .getOrNull();
        if (nodeJsPath != null) {
            setExecutePermissions(nodeJsPath);
            val command = new String[]{nodeJsPath.toString(), "-v"};
            val process = getRuntime().exec(command);
            if (!process.waitFor(5, SECONDS)) {
                process.destroyForcibly();
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

            String content = readStringFromStream(process.getInputStream()).trim();
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

    @SneakyThrows
    private static void setExecutePermissions(Path path) {
        try {
            val permissionsToSet = EnumSet.of(OWNER_EXECUTE, GROUP_EXECUTE, OTHERS_EXECUTE);
            permissionsToSet.addAll(getPosixFilePermissions(path));
            setPosixFilePermissions(path, permissionsToSet);

        } catch (UnsupportedOperationException ignored) {
            // do nothing
        } catch (Exception e) {
            logger.debug(e.toString(), e);
        }
    }


    @Nullable
    Path nodeJsPath;

    @Nullable
    String version;

}
