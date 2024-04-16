package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.nio.file.Files.getPosixFilePermissions;
import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static name.remal.gradle_plugins.toolkit.CrossCompileServices.loadCrossCompileService;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;
import static name.remal.gradle_plugins.toolkit.TimeoutUtils.withTimeout;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.unwrapGeneratedSubclass;

import java.io.File;
import java.time.Duration;
import java.util.EnumSet;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.val;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ProviderFactory;

@CustomLog
abstract class NodeJsDetector implements Comparable<NodeJsDetector> {

    private static final NodeJsExecutableMethods METHODS = loadCrossCompileService(NodeJsExecutableMethods.class);
    private static final Pattern NODE_VERSION_OUTPUT = Pattern.compile("v?(\\d+(?:\\.\\d+){0,10})(-\\S*)?");


    @Nullable
    public File detectDefaultNodeJsExecutable() {
        return null;
    }

    @Nullable
    public File detectNodeJsExecutable(String version) {
        return null;
    }


    public int getOrder() {
        return 0;
    }

    @Override
    @SuppressWarnings("java:S1210")
    public int compareTo(NodeJsDetector other) {
        int result = Integer.compare(getOrder(), other.getOrder());
        if (result == 0) {
            result = getClass().getName().compareTo(other.getClass().getName());
        }
        return result;
    }


    protected final void setExecutePermissions(File file) {
        try {
            val filePath = file.toPath();
            val permissionsToSet = EnumSet.of(OWNER_EXECUTE, GROUP_EXECUTE, OTHERS_EXECUTE);
            permissionsToSet.addAll(getPosixFilePermissions(filePath));
            setPosixFilePermissions(filePath, permissionsToSet);

        } catch (UnsupportedOperationException ignored) {
            // do nothing
        } catch (Exception e) {
            Logging.getLogger(unwrapGeneratedSubclass(getClass())).warn(e.toString(), e);
        }
    }

    @SuppressWarnings({"java:S1193", "java:S2142"})
    protected final NodeJsVersionResult getNodeJsVersion(File file) {
        try {
            return withTimeout(Duration.ofSeconds(5), () -> {
                val result = METHODS.getNodeJsVersion(getProviders(), file);
                val versionString = result.getVersion();
                if (isEmpty(versionString)) {
                    return result;
                }

                val matcher = NODE_VERSION_OUTPUT.matcher(versionString);
                if (matcher.matches()) {
                    return result;
                }

                return NodeJsVersionResult.error(format(
                    "%s produced output which is not a Node.js version:%n%s",
                    file,
                    versionString
                ));
            });

        } catch (Throwable e) {
            return NodeJsVersionResult.error(e);
        }
    }


    @Inject
    protected abstract ProviderFactory getProviders();

}
