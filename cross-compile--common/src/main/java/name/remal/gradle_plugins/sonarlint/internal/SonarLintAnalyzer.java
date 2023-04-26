package name.remal.gradle_plugins.sonarlint.internal;

import java.util.Collection;
import name.remal.gradle_plugins.toolkit.issues.Issue;

public interface SonarLintAnalyzer {

    /**
     * This method uses generic {@link Object} type, otherwise classes relocation will break compilation.
     *
     * @return a collection of {@link Issue}
     */
    @SuppressWarnings("java:S1452")
    Collection<?> analyze(SonarLintExecutionParams params);

}
