package name.remal.gradle_plugins.sonarlint.internal;

import lombok.val;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;

public interface StandaloneGlobalConfigurationFactory {

    StandaloneGlobalConfiguration create(SonarLintExecutionParams params);


    static StandaloneGlobalConfiguration createEngineConfig(SonarLintExecutionParams params) {
        val factory = SonarLintServices.loadSonarLintService(
            StandaloneGlobalConfigurationFactory.class,
            params.getSonarLintVersion().get()
        );
        return factory.create(params);
    }

}
