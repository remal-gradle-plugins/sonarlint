package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.lang.String.format;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintConfigurationUtils.loadPlugins;

import java.io.IOException;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

abstract class AbstractExtractorContainer extends RulesDefinitionExtractorContainer {

    @SuppressWarnings("Slf4jLoggerShouldBePrivate")
    protected final Logger logger = Logging.getLogger(this.getClass());

    private final PluginsLoadResult pluginsLoadResult;

    protected AbstractExtractorContainer(SonarLintExecutionParams params) {
        this(loadPlugins(params));
    }

    @SuppressWarnings("java:S1144")
    private AbstractExtractorContainer(PluginsLoadResult pluginsLoadResult) {
        super(pluginsLoadResult.getLoadedPlugins().getPluginInstancesByKeys());
        this.pluginsLoadResult = pluginsLoadResult;
    }

    @Override
    @SuppressWarnings("Slf4jFormatShouldBeConst")
    public SpringComponentContainer stopComponents() {
        try {
            return super.stopComponents();

        } finally {
            try {
                pluginsLoadResult.close();
            } catch (IOException e) {
                logger.error(format("Error closing %s: %s", pluginsLoadResult.getClass().getSimpleName(), e), e);
            }
        }
    }

}
