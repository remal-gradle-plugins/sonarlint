package name.remal.gradle_plugins.sonarlint;

import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.toolkit.FunctionUtils.toSubstringedBefore;
import static name.remal.gradle_plugins.toolkit.UrlUtils.readStringFromUrl;

import com.google.common.base.Splitter;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import org.gradle.api.model.ObjectFactory;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
@CustomLog
abstract class NodeJsDetectors {


    private final File rootDir;


    private static final Lock DETECT_LOCK = new ReentrantLock();

    private final List<NodeJsDetector> detectors = loadNodeJsDetectors();

    @Nullable
    public NodeJsFound detectNodeJsExecutable() {
        DETECT_LOCK.lock();
        try {
            return detectNodeJsExecutableImpl();

        } finally {
            DETECT_LOCK.unlock();
        }
    }

    @Nullable
    private NodeJsFound detectNodeJsExecutableImpl() {
        return detectors.stream()
            .map(NodeJsDetector::detectNodeJsExecutable)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }


    @SuppressWarnings("java:S3864")
    private List<NodeJsDetector> loadNodeJsDetectors() {
        return loadNodeJsDetectorClasses().stream()
            .map(clazz -> getObjects().newInstance(clazz))
            .peek(object -> {
                if (object instanceof NodeJsDetectorWithRootDir) {
                    ((NodeJsDetectorWithRootDir) object).setRootDir(rootDir);
                }
            })
            .sorted()
            .collect(toList());
    }

    @SneakyThrows
    private List<Class<NodeJsDetector>> loadNodeJsDetectorClasses() {
        val classNames = new LinkedHashSet<String>();
        val classLoader = Optional.ofNullable(NodeJsDetectors.class.getClassLoader())
            .orElseGet(ClassLoader::getSystemClassLoader);
        val resourceUrls = classLoader.getResources("META-INF/services/" + NodeJsDetector.class.getName());
        while (resourceUrls.hasMoreElements()) {
            val resourceUrl = resourceUrls.nextElement();
            val resourceContent = readStringFromUrl(resourceUrl);
            Splitter.onPattern("[\\r\\n]+").splitToStream(resourceContent)
                .map(toSubstringedBefore("#"))
                .map(String::trim)
                .filter(ObjectUtils::isNotEmpty)
                .forEach(classNames::add);
        }

        val classes = new ArrayList<Class<NodeJsDetector>>(classNames.size());
        for (val className : classNames) {
            @SuppressWarnings("unchecked")
            val clazz = (Class<NodeJsDetector>) Class.forName(className, true, NodeJsDetectors.class.getClassLoader());
            classes.add(clazz);
        }
        return classes;
    }


    @Inject
    protected abstract ObjectFactory getObjects();

}
