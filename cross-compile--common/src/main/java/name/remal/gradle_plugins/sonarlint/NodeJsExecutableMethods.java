package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.nio.file.Files.getPosixFilePermissions;
import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;

import java.io.File;
import java.util.EnumSet;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.CustomLog;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsInfo;
import org.gradle.api.provider.ProviderFactory;

@CustomLog
abstract class NodeJsExecutableMethods {

    public abstract NodeJsInfo getNodeJsInfo(ProviderFactory providers, File file);


    @SuppressWarnings("Slf4jFormatShouldBeConst")
    protected static void setExecutePermissionsIfNeeded(File file) {
        file = file.getAbsoluteFile();
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
    protected static String parseVersion(String output) {
        var matcher = NODE_VERSION_OUTPUT.matcher(output.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }

        return null;
    }

}
