package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.util.stream.Collectors.toUnmodifiableList;
import static lombok.AccessLevel.PRIVATE;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Unmodifiable;

@NoArgsConstructor(access = PRIVATE)
public abstract class ForkUtils {

    @Unmodifiable
    public static Map<String, String> getEnvironmentVariablesToSetToForkedProcess() {
        return Map.of();
    }

    @Unmodifiable
    public static List<String> getEnvironmentVariablesToPropagateToForkedProcess() {
        return Stream.of(
                "NAME_REMAL_GRADLE_PLUGINS_"
            )
            .filter(name -> System.getenv().keySet().stream()
                .anyMatch(it -> it.startsWith(name))
            )
            .collect(toUnmodifiableList());
    }


    @Unmodifiable
    public static Map<String, String> getSystemsPropertiesToSetToForkedProcess() {
        return Map.of(
            "java.awt.headless", "true"
        );
    }

    @Unmodifiable
    public static List<String> getSystemsPropertiesToPropagateToForkedProcess() {
        return List.of(
            "java.io.tmpdir",
            "file.encoding",
            "user.timezone",
            "user.country",
            "user.language",
            "http.keepAlive",
            "sun.net.client.defaultConnectTimeout",
            "sun.net.client.defaultReadTimeout",
            "sun.net.http.retryPost",
            "sun.io.useCanonCaches"
        );
    }

}
