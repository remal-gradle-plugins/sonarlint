package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.toolkit.SourceSetUtils.whenTestSourceSetRegistered;

import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;

public abstract class SonarLintExtension extends SonarLintSettings {

    @Getter
    private final SonarLintLanguagesSettings languages = getObjects().newInstance(SonarLintLanguagesSettings.class);

    public void languages(Action<? super SonarLintLanguagesSettings> action) {
        action.execute(getLanguages());
    }


    public abstract SetProperty<SourceSet> getTestSourceSets();

    {
        whenTestSourceSetRegistered(getProject(), getTestSourceSets()::add);
    }


    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract Project getProject();

}
