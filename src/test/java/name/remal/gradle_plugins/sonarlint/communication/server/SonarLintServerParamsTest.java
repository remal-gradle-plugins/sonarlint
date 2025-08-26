package name.remal.gradle_plugins.sonarlint.communication.server;

import java.io.File;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import org.junit.jupiter.api.Test;

class SonarLintServerParamsTest {

    @Test
    void serialization() {
        var params = SonarLintServerParams.builder()
            .pluginFile(new File("plugin-1.jar"))
            .pluginFile(new File("plugin-2.jar"))
            .enabledPluginLanguage(SonarLintLanguage.JAVA)
            .enabledPluginLanguage(SonarLintLanguage.KOTLIN)
            .build();

        var pluginFiles = params.getPluginFiles();
        var enabledPluginLanguages = params.getEnabledPluginLanguages();

        var bytes = params.serializeToBytes();
        var deserialized = SonarLintServerParams.deserializeFromBytes(bytes);

        assertEquals(pluginFiles, deserialized.getPluginFiles());
        assertEquals(enabledPluginLanguages, deserialized.getEnabledPluginLanguages());
    }

}
