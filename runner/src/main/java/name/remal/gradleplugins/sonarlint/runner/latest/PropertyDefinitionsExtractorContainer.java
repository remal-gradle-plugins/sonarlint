package name.remal.gradleplugins.sonarlint.runner.latest;

import static name.remal.gradleplugins.toolkit.ObjectUtils.defaultValue;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.val;
import name.remal.gradleplugins.sonarlint.shared.RunnerParams;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;

@Getter
class PropertyDefinitionsExtractorContainer extends AbstractExtractorContainer {

    private final List<PropertyDefinition> propertyDefinitions = new ArrayList<>();

    private final RunnerParams params;

    public PropertyDefinitionsExtractorContainer(RunnerParams params, StandaloneGlobalConfiguration globalConfig) {
        super(globalConfig);
        this.params = params;
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
                    + ", a value of this property will be taken as Node.js version"
                )
                .defaultValue(defaultValue(params.getDefaultNodeJsVersion()))
                .build()
        );
    }

    @Override
    protected void doAfterStart() {
        val propertyDefinitions = getComponentByType(PropertyDefinitions.class).getAll();
        this.propertyDefinitions.addAll(propertyDefinitions);
    }

}
