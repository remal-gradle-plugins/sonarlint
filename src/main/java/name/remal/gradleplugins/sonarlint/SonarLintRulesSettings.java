package name.remal.gradleplugins.sonarlint;

import static com.google.common.collect.Maps.immutableEntry;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.gradle.api.Action;
import org.gradle.api.Project;

@Getter
@Setter
public class SonarLintRulesSettings {

    private final Project project;

    @Inject
    public SonarLintRulesSettings(Project project) {
        this.project = project;
    }


    private Boolean disableConflictingWithLombok;


    private final Collection<String> enabled = new ArrayList<>();

    private final Collection<String> disabled = new ArrayList<>();

    private final Map<String, SonarLintRuleSettings> rulesSettings = new LinkedHashMap<>();

    public void enable(String... rules) {
        enabled.addAll(asList(rules));
    }

    public void disable(String... rules) {
        disabled.addAll(asList(rules));
    }

    public void rule(String rule, Action<SonarLintRuleSettings> action) {
        val ruleSettings = rulesSettings.computeIfAbsent(
            rule,
            __ -> project.getObjects().newInstance(SonarLintRuleSettings.class)
        );
        action.execute(ruleSettings);
    }


    Map<String, Map<String, Object>> getProperties() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        rulesSettings.entrySet().stream()
            .map(entry -> immutableEntry(entry.getKey(), entry.getValue().getProperties()))
            .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

}
