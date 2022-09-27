package name.remal.gradleplugins.sonarlint.runner.latest;

import java.util.Optional;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

abstract class AbstractExtractorContainer extends RulesDefinitionExtractorContainer {

    protected AbstractExtractorContainer(StandaloneGlobalConfiguration globalConfig) {
        super(new PluginInstancesRepository(new Configuration(
            globalConfig.getPluginPaths(),
            globalConfig.getEnabledLanguages(),
            Optional.ofNullable(globalConfig.getNodeJsVersion())
        )));
    }

}
