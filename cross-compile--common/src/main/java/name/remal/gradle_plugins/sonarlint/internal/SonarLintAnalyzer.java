package name.remal.gradle_plugins.sonarlint.internal;

import java.util.List;
import name.remal.gradle_plugins.toolkit.issues.Issue;

public interface SonarLintAnalyzer {

    /**
     * This method uses generic {@link Object} type, otherwise classes relocation will break compilation.
     *
     * @return a list of {@link Issue}
     */
    @SuppressWarnings("java:S1452")
    public abstract List<?> analyze(SonarLintExecutionParams params);

}
