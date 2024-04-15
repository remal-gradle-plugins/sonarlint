package name.remal.gradle_plugins.sonarlint;

import static java.nio.charset.StandardCharsets.UTF_8;
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

    @Nullable
    @SuppressWarnings({"java:S1193", "java:S2142"})
    protected final String getNodeJsVersion(File file) {
        try {
            return withTimeout(Duration.ofSeconds(5), () -> {
                val bytes = METHODS.executeNodeJsVersion(getProviders(), file);
                if (bytes == null) {
                    return null;
                }

                val content = new String(bytes, UTF_8).trim();
                val matcher = NODE_VERSION_OUTPUT.matcher(content);
                if (matcher.matches()) {
                    return matcher.group(1);
                }

                return null;
            });

        } catch (Throwable ignore) {
            return null;
        }
    }

    @Nullable
    protected final Integer getNodeJsMajorVersion(File file) {
        String version = getNodeJsVersion(file);
        if (version == null) {
            return null;
        }

        int delim = version.indexOf('.');
        if (delim > 0) {
            version = version.substring(0, delim);
        }

        try {
            return Integer.parseInt(version);

        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    protected final void checkNodeJsExecutableForTests(File file) {
        if (isInTest()) {
            val version = getNodeJsVersion(file);
            if (isEmpty(version)) {
                throw new AssertionError("Not a Node.js executable: " + file);
            }
        }
    }


    @Inject
    protected abstract ProviderFactory getProviders();

}
