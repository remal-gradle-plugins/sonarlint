package name.remal.gradleplugins.sonarlint;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.CodeQualityExtension;
import org.gradle.api.tasks.SourceSet;

@Getter
@Setter
public class SonarLintExtension extends CodeQualityExtension {

    private Collection<SourceSet> testSourceSets;


    private final SonarLintRulesSettings rules;

    private final Map<String, Object> sonarProperties = new LinkedHashMap<>();

    private final SonarLintForkOptions fork;

    @Inject
    public SonarLintExtension(Project project) {
        this.rules = project.getObjects().newInstance(SonarLintRulesSettings.class, project);
        this.fork = project.getObjects().newInstance(SonarLintForkOptions.class);
    }


    public void rules(Action<SonarLintRulesSettings> action) {
        action.execute(rules);
    }

    public void property(String key, @Nullable Object value) {
        sonarProperties.put(key, value);
    }

    public void fork(Action<SonarLintForkOptions> action) {
        action.execute(fork);
    }

}
