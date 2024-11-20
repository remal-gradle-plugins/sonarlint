package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.util.Optional;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;

public class SonarLintPropertiesDocumentationCollector {

    public PropertiesDocumentation collectPropertiesDocumentation(SonarLintExecutionParams params) {
        val propertyDefinitionsExtractor = new SonarLintPropertyDefinitionsExtractorContainer(params);
        propertyDefinitionsExtractor.execute();

        val currentProperties = params.getSonarProperties().get();

        val propertiesDoc = new PropertiesDocumentation();
        propertyDefinitionsExtractor
            .getPropertyDefinitions()
            .forEach(propDef -> propertiesDoc.property(propDef.key(), propDoc -> {
                propDoc.setName(propDef.name());
                propDoc.setDescription(propDef.description());
                Optional.ofNullable(propDef.type())
                    .map(Enum::name)
                    .ifPresent(propDoc::setType);
                propDoc.setCurrentValue(currentProperties.get(propDef.key()));
                propDoc.setDefaultValue(propDef.defaultValue());
            }));
        return propertiesDoc;
    }

}
