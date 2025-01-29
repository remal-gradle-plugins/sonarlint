package name.remal.gradle_plugins.sonarlint.settings;

import org.gradle.api.Action;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

public interface SonarLintSettings extends WithSonarLintForkSettings {

    @Nested
    SonarLintNodeJsSettings getNodeJs();

    default void nodeJs(Action<SonarLintNodeJsSettings> action) {
        action.execute(getNodeJs());
    }


    @Nested
    SonarLintRulesSettings getRules();

    default void rules(Action<SonarLintRulesSettings> action) {
        action.execute(getRules());
    }


    @Input
    @org.gradle.api.tasks.Optional
    MapProperty<String, Object> getSonarProperties();

    default void sonarProperty(String key, Object value) {
        getSonarProperties().put(key, value);
    }


    @Nested
    SonarLintLoggingSettings getLogging();

    default void logging(Action<SonarLintLoggingSettings> action) {
        action.execute(getLogging());
    }

}
