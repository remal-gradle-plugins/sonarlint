package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.GradleException;

public class NodeJsDetectorException extends GradleException {

    NodeJsDetectorException(String message) {
        super(message);
    }

}
