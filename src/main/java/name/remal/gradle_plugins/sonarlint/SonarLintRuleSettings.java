package name.remal.gradle_plugins.sonarlint;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SonarLintRuleSettings {

    private Map<String, Object> properties = new LinkedHashMap<>();

    public void property(String key, @Nullable Object value) {
        properties.put(key, value);
    }

}
