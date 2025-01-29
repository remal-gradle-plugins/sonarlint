package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.getPosixFilePermissions;
import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound.nodeJsNotFound;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.TimeoutUtils.withTimeout;

import java.io.File;
import java.time.Duration;
import java.util.EnumSet;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.gradle.api.provider.ProviderFactory;

@CustomLog
@RequiredArgsConstructor(onConstructor_ = {@Inject})
@SuppressWarnings("Slf4jFormatShouldBeConst")
abstract class NodeJsInfoRetriever {

    public NodeJsInfo getNodeJsInfo(File file) {
        try {
            var normalizedFile = normalizeFile(file);
            return withTimeout(Duration.ofSeconds(5), () ->
                getNodeJsInfoImpl(normalizedFile)
            );

        } catch (Throwable e) {
            logger.debug(e.toString(), e);
            return nodeJsNotFound(e);
        }
    }

    @SneakyThrows
    private NodeJsInfo getNodeJsInfoImpl(File file) {
        setExecutePermissionsIfNeeded(file);

        var command = new String[]{file.getAbsolutePath(), "--version"};
        var execResult = getProviders().exec(spec -> {
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

    private static void setExecutePermissionsIfNeeded(File file) {
        if (file.canExecute()) {
            return;
        }

        try {
            var filePath = file.toPath();
            var permissionsToSet = EnumSet.of(OWNER_EXECUTE, GROUP_EXECUTE, OTHERS_EXECUTE);
            permissionsToSet.addAll(getPosixFilePermissions(filePath));
            setPosixFilePermissions(filePath, permissionsToSet);

        } catch (UnsupportedOperationException ignored) {
            // do nothing
        } catch (Exception e) {
            logger.warn(format("Error setting execute permissions for %s: %s", file, e), e);
        }
    }


    private static final Pattern NODE_VERSION_OUTPUT = Pattern.compile("v?(\\d+(?:\\.\\d+){0,10}(-\\S*)?)");

    @Nullable
    private static String parseVersion(String output) {
        var matcher = NODE_VERSION_OUTPUT.matcher(output.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }

        return null;
    }


    @Inject
    protected abstract ProviderFactory getProviders();

}
