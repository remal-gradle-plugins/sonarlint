package name.remal.gradle_plugins.sonarlint.settings;

import org.gradle.api.Action;
import org.gradle.api.tasks.Nested;

public interface WithSonarLintForkSettings {

    @Nested
    SonarLintForkSettings getFork();

    default void fork(Action<SonarLintForkSettings> action) {
        action.execute(getFork());
    }

}
