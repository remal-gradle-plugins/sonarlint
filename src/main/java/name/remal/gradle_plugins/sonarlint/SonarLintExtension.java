package name.remal.gradle_plugins.sonarlint;

import java.util.Collection;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.CodeQualityExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

@Getter
@Setter
public abstract class SonarLintExtension extends CodeQualityExtension {

    private Collection<SourceSet> testSourceSets;


    public abstract Property<Boolean> getIsGeneratedCodeIgnored();

    {
        getIsGeneratedCodeIgnored().convention(true);
    }


    private final SonarLintRulesSettings rules = getObjectFactory().newInstance(SonarLintRulesSettings.class);

    public void rules(Action<SonarLintRulesSettings> action) {
        action.execute(rules);
    }


    public abstract MapProperty<String, Object> getSonarProperties();

    public void sonarProperty(String key, Object value) {
        getSonarProperties().put(key, value);
    }


    public abstract ListProperty<String> getIgnoredPaths();


    private final SonarLintForkOptions fork = getObjectFactory().newInstance(SonarLintForkOptions.class);

    public void fork(Action<SonarLintForkOptions> action) {
        action.execute(fork);
    }


    @Inject
    protected abstract ObjectFactory getObjectFactory();

}
