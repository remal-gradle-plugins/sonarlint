package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;

class SonarLintPropertyDefinitionsExtractorContainer extends AbstractDefinitionExtractorContainer {

    @Getter
    private final List<PropertyDefinition> propertyDefinitions = new ArrayList<>();

    public SonarLintPropertyDefinitionsExtractorContainer(SonarLintExecutionParams params) {
        super(params);
    }

    @Override
    protected void doAfterStart() {
        var propertyDefinitions = getComponentByType(PropertyDefinitions.class).getAll();
        this.propertyDefinitions.addAll(propertyDefinitions);
    }

}
