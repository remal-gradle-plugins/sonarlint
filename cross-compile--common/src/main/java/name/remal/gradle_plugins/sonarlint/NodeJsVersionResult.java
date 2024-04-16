package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = PRIVATE)
class NodeJsVersionResult {

    public static NodeJsVersionResult of(String version) {
        return new NodeJsVersionResult(version.trim(), null);
    }

    public static NodeJsVersionResult error(Throwable error) {
        return new NodeJsVersionResult(null, error);
    }

    public static NodeJsVersionResult error(String error) {
        return error(new NodeJsDetectorException(error));
    }


    @Nullable
    String version;

    @Nullable
    Throwable error;

}
