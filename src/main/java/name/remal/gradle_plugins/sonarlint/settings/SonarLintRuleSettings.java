package name.remal.gradle_plugins.sonarlint.settings;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;

public interface SonarLintRuleSettings {

    @Input
    @org.gradle.api.tasks.Optional
    MapProperty<String, Object> getProperties();

    default void property(String key, Object value) {
        getProperties().put(key, value);
    }


    @Input
    @org.gradle.api.tasks.Optional
    ListProperty<String> getIgnoredPaths();

}
