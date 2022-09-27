package name.remal.gradleplugins.sonarlint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.RequiredArgsConstructor;
import org.gradle.api.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SonarLintPluginTest {

    final Project project;

    @BeforeEach
    void beforeEach() {
        project.getPluginManager().apply(SonarLintPlugin.class);
    }

    @Test
    void test() {
        assertTrue(project.getPlugins().hasPlugin(SonarLintPlugin.class));
    }

}
