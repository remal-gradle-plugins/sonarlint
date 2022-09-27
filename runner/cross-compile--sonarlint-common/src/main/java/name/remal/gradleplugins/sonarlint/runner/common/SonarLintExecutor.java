package name.remal.gradleplugins.sonarlint.runner.common;

import java.util.List;
import name.remal.gradleplugins.sonarlint.shared.RunnerParams;
import name.remal.gradleplugins.toolkit.issues.Issue;

/**
 * This interface uses generic {@link Object} type everywhere, otherwise classes relocation will break compilation.
 */
public interface SonarLintExecutor {

    /**
     * @param untypedParams as instance of {@link RunnerParams}
     */
    void init(Object untypedParams);

    /**
     * @return a list of {@link Issue}
     */
    @SuppressWarnings("java:S1452")
    List<?> analyze();

    /**
     * @return an instance of {@link Documentation}
     */
    Object collectRulesDocumentation();

    /**
     * @return an instance of {@link Documentation}
     */
    Object collectPropertiesDocumentation();

}
