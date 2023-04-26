package name.remal.gradle_plugins.sonarlint;

import lombok.Getter;
import lombok.Setter;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;

@Getter
@Setter
public abstract class SonarLintRuleSettings {

    public abstract MapProperty<String, Object> getProperties();

    public void property(String key, Object value) {
        getProperties().put(key, value);
    }


    public abstract ListProperty<String> getIgnoredPaths();

}
