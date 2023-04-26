package name.remal.gradle_plugins.sonarlint;

import static java.util.Arrays.asList;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

@Getter
@Setter
public abstract class SonarLintRulesSettings {

    public abstract Property<Boolean> getDisableConflictingWithLombok();


    public abstract SetProperty<String> getEnabled();

    public void enable(String... rules) {
        getEnabled().addAll(asList(rules));
    }


    public abstract SetProperty<String> getDisabled();

    public void disable(String... rules) {
        getDisabled().addAll(asList(rules));
    }


    private final Map<String, SonarLintRuleSettings> rulesSettings = new LinkedHashMap<>();

    public void rule(String rule, Action<SonarLintRuleSettings> action) {
        val ruleSettings = rulesSettings.computeIfAbsent(
            rule,
            __ -> getObjectFactory().newInstance(SonarLintRuleSettings.class)
        );
        action.execute(ruleSettings);
    }


    Map<String, Map<String, Object>> buildProperties() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        rulesSettings.forEach((ruleId, settings) -> {
            val properties = settings.getProperties().get();
            if (isNotEmpty(properties)) {
                result.put(ruleId, properties);
            }
        });
        return result;
    }

    Map<String, List<String>> buildIgnoredPaths() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        rulesSettings.forEach((ruleId, settings) -> {
            val ignoredPaths = settings.getIgnoredPaths().get();
            if (isNotEmpty(ignoredPaths)) {
                result.put(ruleId, ignoredPaths);
            }
        });
        return result;
    }


    @Inject
    protected abstract ObjectFactory getObjectFactory();

}
