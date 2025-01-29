package name.remal.gradle_plugins.sonarlint.settings;

import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

@Getter
public abstract class SonarLintRulesSettings {

    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<Boolean> getDisableConflictingWithLombok();


    @Input
    @org.gradle.api.tasks.Optional
    public abstract SetProperty<String> getEnabled();

    public void enable(String... rules) {
        getEnabled().addAll(List.of(rules));
    }


    @Input
    @org.gradle.api.tasks.Optional
    public abstract SetProperty<String> getDisabled();

    public void disable(String... rules) {
        getDisabled().addAll(List.of(rules));
    }


    @Nested
    @org.gradle.api.tasks.Optional
    public abstract MapProperty<String, SonarLintRuleSettings> getRulesSettings();

    public void rule(String rule, Action<SonarLintRuleSettings> action) {
        var settings = getRulesSettings().getting(rule).getOrNull();
        if (settings == null) {
            settings = getObjects().newInstance(SonarLintRuleSettings.class);
            getRulesSettings().put(rule, settings);
        }
        action.execute(settings);
    }


    @Inject
    protected abstract ObjectFactory getObjects();

}
