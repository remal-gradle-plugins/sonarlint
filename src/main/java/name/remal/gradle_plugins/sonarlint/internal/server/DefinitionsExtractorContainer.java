package name.remal.gradle_plugins.sonarlint.internal.server;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.utils.AnnotationUtils;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.plugin.commons.ExtensionInstaller;
import org.sonarsource.sonarlint.core.plugin.commons.ExtensionUtils;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;
import org.sonarsource.sonarlint.core.plugin.commons.sonarapi.ConfigurationBridge;
import org.sonarsource.sonarlint.core.rule.extractor.EmptyConfiguration;
import org.sonarsource.sonarlint.core.rule.extractor.RuleDefinitionsLoader;
import org.sonarsource.sonarlint.core.rule.extractor.RuleExtractionSettings;
import org.sonarsource.sonarlint.core.rule.extractor.RuleSettings;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

class DefinitionsExtractorContainer extends SpringComponentContainer {

    public DefinitionsExtractorContainer(SpringComponentContainer parent) {
        super(parent);
    }

    @Override
    protected void doBeforeStart() {
        var parent = requireNonNull(this.parent);

        var sonarLintRuntime = parent.getComponentByType(SonarLintRuntime.class);
        var extensionInstaller = new ExtensionInstaller(sonarLintRuntime, new EmptyConfiguration());

        var loadedPlugins = parent.getComponentByType(LoadedPlugins.class);
        extensionInstaller.install(this, loadedPlugins.getAllPluginInstancesByKeys(), (key, ext) -> {
            if (ExtensionUtils.isType(ext, Sensor.class)) {
                // Optimization, and allows to run with the Xoo plugin
                return false;
            }

            var annotation = AnnotationUtils.getAnnotation(ext, SonarLintSide.class);
            if (annotation != null) {
                var lifespan = annotation.lifespan();
                return SonarLintSide.SINGLE_ANALYSIS.equals(lifespan);
            }

            return false;
        });

        add(
            new RuleSettings(Map.of()),
            ConfigurationBridge.class,
            RuleExtractionSettings.class,
            RuleDefinitionsLoader.class
        );
    }

}
