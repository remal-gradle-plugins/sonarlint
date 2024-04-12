package name.remal.gradle_plugins.sonarlint;

import static java.io.File.pathSeparatorChar;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.NodeJsVersions.LATEST_NODEJS_LTS_MAJOR_VERSION;
import static name.remal.gradle_plugins.sonarlint.OsDetector.DETECTED_OS;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultValue;
import static name.remal.gradle_plugins.toolkit.ProviderFactoryUtils.getEnvironmentVariable;
import static name.remal.gradle_plugins.toolkit.internal.Flags.isInTest;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.tisonkun.os.core.OS;
import java.io.File;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.val;
import name.remal.gradle_plugins.toolkit.ObjectUtils;

@CustomLog
@RequiredArgsConstructor(onConstructor_ = {@Inject})
abstract class NodeJsDetectorOnPath extends NodeJsDetector {

    private static final List<String> PATH_ENVIRONMENT_VARIABLE_NAMES = ImmutableList.of(
        DETECTED_OS.os == OS.windows ? "Path" : "",
        "PATH"
    );

    private static final String FILE_NAME_TO_SEARCH = format(
        "node%s",
        DETECTED_OS.os == OS.windows ? ".exe" : ""
    );

    @Nullable
    @Override
    public File detectDefaultNodeJsExecutable() {
        if (isInTest()) {
            return null;
        }

        val path = PATH_ENVIRONMENT_VARIABLE_NAMES.stream()
            .filter(ObjectUtils::isNotEmpty)
            .map(name -> getEnvironmentVariable(getProviders(), name))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (path == null) {
            return null;
        }

        val pathElements = Splitter.on(pathSeparatorChar).splitToStream(defaultValue(path))
            .map(String::trim)
            .filter(ObjectUtils::isNotEmpty)
            .collect(toList());
        for (val pathElement : pathElements) {
            val candidateFile = new File(pathElement, FILE_NAME_TO_SEARCH);
            if (candidateFile.isFile() && candidateFile.canExecute()) {
                val majorVersion = getNodeJsMajorVersion(candidateFile);
                if (majorVersion == null
                    || majorVersion < LATEST_NODEJS_LTS_MAJOR_VERSION
                ) {
                    continue;
                }

                return candidateFile;
            }
        }

        return null;
    }


    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

}
