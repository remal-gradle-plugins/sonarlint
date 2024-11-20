package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.SonarDependencies.getSonarDependency;

import java.util.Collection;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
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
@CustomLog
public abstract class SonarLintExtension extends CodeQualityExtension {

    @Override
    public final String getToolVersion() {
        return getSonarDependency("sonarlint-core").getVersion();
    }

    @Override
    public final void setToolVersion(@Nullable String toolVersion) {
        logger.warn("SonarLint tool version is readonly and can't be changed");
    }


    private Collection<SourceSet> testSourceSets;


    public abstract Property<Boolean> getIsGeneratedCodeIgnored();

    {
        getIsGeneratedCodeIgnored().convention(true);
    }


    private final SonarLintNodeJs nodeJs = getObjects().newInstance(SonarLintNodeJs.class);

    public void nodeJs(Action<SonarLintNodeJs> action) {
        action.execute(nodeJs);
    }


    private final SonarLintRulesSettings rules = getObjects().newInstance(SonarLintRulesSettings.class);

    public void rules(Action<SonarLintRulesSettings> action) {
        action.execute(rules);
    }


    private final SonarLintLanguagesSettings languages =
        getObjects().newInstance(SonarLintLanguagesSettings.class);

    public void languages(Action<SonarLintLanguagesSettings> action) {
        action.execute(languages);
    }


    public abstract MapProperty<String, Object> getSonarProperties();

    public void sonarProperty(String key, Object value) {
        getSonarProperties().put(key, value);
    }


    public abstract ListProperty<String> getIgnoredPaths();


    private final SonarLintLoggingOptions logging = getObjects().newInstance(SonarLintLoggingOptions.class);

    {
        nodeJs.getLogNodeJsNotFound().convention(logging.getHideWarnings().map(it -> !it));
    }

    public void logging(Action<SonarLintLoggingOptions> action) {
        action.execute(logging);
    }


    private final SonarLintForkOptions fork = getObjects().newInstance(SonarLintForkOptions.class);

    public void fork(Action<SonarLintForkOptions> action) {
        action.execute(fork);
    }


    @Inject
    protected abstract ObjectFactory getObjects();

}
