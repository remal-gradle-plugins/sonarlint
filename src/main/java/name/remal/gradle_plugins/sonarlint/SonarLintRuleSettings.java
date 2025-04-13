package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

public interface SonarLintRuleSettings {

    @Input
    @org.gradle.api.tasks.Optional
    MapProperty<String, String> getProperties();

    default void property(String key, String value) {
        getProperties().put(key, value);
    }

    default void property(String key, Provider<String> value) {
        getProperties().put(key, value);
    }


    @Input
    @org.gradle.api.tasks.Optional
    ListProperty<String> getIgnoredPaths();

}
