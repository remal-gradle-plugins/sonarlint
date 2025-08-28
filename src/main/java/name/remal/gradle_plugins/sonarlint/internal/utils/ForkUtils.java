package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;

import java.util.List;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Unmodifiable;

@NoArgsConstructor(access = PRIVATE)
public abstract class ForkUtils {

    @Unmodifiable
    public static List<String> getSystemsPropertiesToPropagateToForkedProcess() {
        return List.of(
            "java.io.tmpdir",
            "file.encoding",
            "http.keepAlive",
            "sun.net.client.defaultConnectTimeout",
            "sun.net.client.defaultReadTimeout",
            "sun.net.http.retryPost",
            "sun.io.useCanonCaches"
        );
    }

}
