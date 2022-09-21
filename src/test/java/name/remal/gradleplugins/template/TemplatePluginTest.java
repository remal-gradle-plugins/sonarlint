package name.remal.gradleplugins.template;

import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.RequiredArgsConstructor;
import org.gradle.api.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TemplatePluginTest {

    final Project project;

    @BeforeEach
    void beforeEach() {
        project.getPluginManager().apply(TemplatePlugin.class);
    }

    @Test
    void test() {
        assertTrue(project.getPlugins().hasPlugin(TemplatePlugin.class));
    }

}
