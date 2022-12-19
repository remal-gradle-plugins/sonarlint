package name.remal.gradleplugins.sonarlint.internal;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradleplugins.toolkit.CrossCompileServices.loadCrossCompileService;
import static name.remal.gradleplugins.toolkit.CrossCompileVersionComparator.standardVersionCrossCompileVersionComparator;

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
