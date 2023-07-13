package name.remal.gradle_plugins.sonarlint;

import static java.util.Arrays.asList;

import lombok.Getter;
import lombok.Setter;
import org.gradle.api.provider.ListProperty;

@Getter
@Setter
public abstract class SonarLintLanguagesSettings {

    public abstract ListProperty<String> getIncludes();

    public void include(String... includes) {
        getIncludes().addAll(asList(includes));
    }

    public abstract ListProperty<String> getExcludes();

    public void exclude(String... excludes) {
        getExcludes().addAll(asList(excludes));
    }

}
