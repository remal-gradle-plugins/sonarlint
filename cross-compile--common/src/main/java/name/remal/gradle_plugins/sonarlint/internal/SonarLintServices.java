package name.remal.gradle_plugins.sonarlint.internal;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.CrossCompileServices.loadCrossCompileService;
import static name.remal.gradle_plugins.toolkit.CrossCompileVersionComparator.standardVersionCrossCompileVersionComparator;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public abstract class SonarLintServices {

    public static <T> T loadSonarLintService(Class<T> type, String sonarLintVersion) {
        return loadCrossCompileService(
            type,
            standardVersionCrossCompileVersionComparator("sonarlint", sonarLintVersion)
        );
    }

}
