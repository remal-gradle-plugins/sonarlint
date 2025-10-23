package name.remal.gradle_plugins.sonarlint.internal.server.api;

import static java.util.Collections.synchronizedSet;
import static java.util.UUID.randomUUID;
import static lombok.AccessLevel.PRIVATE;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
abstract class SonarLintAnalyzeParamsUtils {

    private static final Set<String> GENERATED_JOB_IDS = synchronizedSet(new LinkedHashSet<>());

    public static String generateNextAnalyzeJobId() {
        while (true) {
            var id = randomUUID().toString();
            if (GENERATED_JOB_IDS.add(id)) {
                return id;
            }
        }
    }

}
