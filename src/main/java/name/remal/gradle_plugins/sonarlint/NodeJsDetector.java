package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.unwrapGeneratedSubclass;

import javax.annotation.Nullable;
import javax.inject.Inject;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;

abstract class NodeJsDetector implements Comparable<NodeJsDetector> {

    protected final Logger logger = Logging.getLogger(unwrapGeneratedSubclass(getClass()));
    protected final NodeJsInfoRetriever nodeJsInfoRetriever = getObjects().newInstance(NodeJsInfoRetriever.class);
    protected final OsDetector osDetector = getObjects().newInstance(OsDetector.class);


    @Nullable
    public NodeJsFound detectDefaultNodeJsExecutable() {
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


    @Inject
    protected abstract ObjectFactory getObjects();

}
