package name.remal.gradle_plugins.sonarlint.internal.latest;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.val;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;

@Getter
class PropertyDefinitionsExtractorContainer extends AbstractExtractorContainer {

    private final List<PropertyDefinition> propertyDefinitions = new ArrayList<>();

    public PropertyDefinitionsExtractorContainer(StandaloneGlobalConfiguration globalConfig) {
        super(globalConfig);
    }

    @Override
    protected void doAfterStart() {
        val propertyDefinitions = getComponentByType(PropertyDefinitions.class).getAll();
        this.propertyDefinitions.addAll(propertyDefinitions);
    }

}
