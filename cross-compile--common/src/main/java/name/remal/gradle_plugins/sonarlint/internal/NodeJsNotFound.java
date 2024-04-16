package name.remal.gradle_plugins.sonarlint.internal;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class NodeJsNotFound implements NodeJsInfo {

    public static NodeJsNotFound nodeJsNotFound(Throwable error) {
        return NodeJsNotFound.builder()
            .error(error)
            .build();
    }

    public static NodeJsNotFound nodeJsNotFound(String error) {
        return nodeJsNotFound(new NodeJsDetectorException(error));
    }


    @NonNull
    Throwable error;

}
