package name.remal.gradle_plugins.sonarlint;

import static java.io.File.pathSeparatorChar;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.NodeJsVersions.MIN_SUPPORTED_NODEJS_VERSION;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultValue;
import static name.remal.gradle_plugins.toolkit.ProviderFactoryUtils.getEnvironmentVariable;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.tisonkun.os.core.OS;
import java.io.File;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.Version;
import org.gradle.api.provider.ProviderFactory;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
abstract class NodeJsDetectorOnPath extends NodeJsDetector {

    @Nullable
    @Override
    public NodeJsFound detectNodeJsExecutable() {
        if (isInTest()) {
            return null;
        }

        val pathEnvironmentVariableNames = ImmutableList.of(
            osDetector.getDetectedOs().os == OS.windows ? "Path" : "",
            "PATH"
        );
        val fileNameToSearch = format(
            "node%s",
            osDetector.getDetectedOs().os == OS.windows ? ".exe" : ""
        );

        val path = pathEnvironmentVariableNames.stream()
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
            val candidateFile = new File(pathElement, fileNameToSearch);
            if (candidateFile.isFile() && candidateFile.canExecute()) {
                val info = nodeJsInfoRetriever.getNodeJsInfo(candidateFile);
                if (info instanceof NodeJsFound) {
                    val foundInfo = (NodeJsFound) info;

                    val version = Version.parse(foundInfo.getVersion());
                    if (version.compareTo(MIN_SUPPORTED_NODEJS_VERSION) < 0) {
                        logger.info(
                            "Node.js executable on PATH can't be used"
                                + ", as its version `{}` less than min supported version `{}`"
                                + ": {}",
                            version,
                            MIN_SUPPORTED_NODEJS_VERSION,
                            candidateFile
                        );
                        continue;
                    }

                    return foundInfo;
                }
            }
        }

        return null;
    }


    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }


    @Inject
    protected abstract ProviderFactory getProviders();

}
