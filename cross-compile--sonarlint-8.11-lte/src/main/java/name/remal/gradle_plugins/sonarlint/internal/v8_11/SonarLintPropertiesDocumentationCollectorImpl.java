package name.remal.gradle_plugins.sonarlint.internal.v8_11;

import static name.remal.gradle_plugins.sonarlint.internal.StandaloneGlobalConfigurationFactory.createEngineConfig;

import com.google.auto.service.AutoService;
import java.util.Optional;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintPropertiesDocumentationCollector;

@AutoService(SonarLintPropertiesDocumentationCollector.class)
final class SonarLintPropertiesDocumentationCollectorImpl implements SonarLintPropertiesDocumentationCollector {

    @Override
    public PropertiesDocumentation collectPropertiesDocumentation(SonarLintExecutionParams params) {
        val engineConfig = createEngineConfig(params);

        val propertyDefinitionsExtractor = new PropertyDefinitionsExtractorContainer(engineConfig);
        propertyDefinitionsExtractor.execute();

        val propertiesDoc = new PropertiesDocumentation();
        propertyDefinitionsExtractor
            .getPropertyDefinitions()
            .forEach(propDef -> propertiesDoc.property(propDef.key(), propDoc -> {
                propDoc.setName(propDef.name());
                propDoc.setDescription(propDef.description());
                Optional.ofNullable(propDef.type())
                    .map(Enum::name)
                    .ifPresent(propDoc::setType);
                propDoc.setDefaultValue(propDef.defaultValue());
            }));
        return propertiesDoc;
    }

}
