package name.remal.gradle_plugins.sonarlint.internal.latest;

import java.util.Optional;
import lombok.val;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

abstract class AbstractExtractorContainer extends RulesDefinitionExtractorContainer {

    protected AbstractExtractorContainer(StandaloneGlobalConfiguration globalConfig) {
        super(loadPlugins(globalConfig).getLoadedPlugins().getPluginInstancesByKeys());
    }

    private static PluginsLoadResult loadPlugins(StandaloneGlobalConfiguration globalConfig) {
        val config = new Configuration(
            globalConfig.getPluginPaths(),
            globalConfig.getEnabledLanguages(),
            Optional.ofNullable(globalConfig.getNodeJsVersion())
        );
        return new PluginsLoader().load(config);
    }

}
