package name.remal.gradle_plugins.sonarlint;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class SonarDependency {

    SonarDependencyType type;

    String group;
    String name;
    String version;

    public String getNotation() {
        return getGroup() + ':' + getName() + ':' + getVersion();
    }

}
