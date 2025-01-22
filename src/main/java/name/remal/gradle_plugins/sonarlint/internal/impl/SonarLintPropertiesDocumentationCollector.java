package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.util.Optional;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;

public class SonarLintPropertiesDocumentationCollector {

    public PropertiesDocumentation collectPropertiesDocumentation(SonarLintExecutionParams params) {
        var propertyDefinitionsExtractor = new SonarLintPropertyDefinitionsExtractorContainer(params);
        propertyDefinitionsExtractor.execute();

        var currentProperties = params.getSonarProperties().get();

        var propertiesDoc = new PropertiesDocumentation();
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
