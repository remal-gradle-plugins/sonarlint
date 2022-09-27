package name.remal.gradleplugins.sonarlint.runner.latest;

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
    protected void doBeforeStart() {
        super.doBeforeStart();

        add(
            PropertyDefinition.builder("sonar.nodejs.executable")
                .name("Absolute path to Node.js executable")
                .build(),
            PropertyDefinition.builder("sonar.nodejs.version")
                .name("Node.js executable version")
                .description("If 'sonar.nodejs.executable' property is not set or empty"
                    + ", value if this property will be taken as Node.js version"
                )
                .build()
        );
    }

    @Override
    protected void doAfterStart() {
        val propertyDefinitions = getComponentByType(PropertyDefinitions.class).getAll();
        this.propertyDefinitions.addAll(propertyDefinitions);
    }

}
