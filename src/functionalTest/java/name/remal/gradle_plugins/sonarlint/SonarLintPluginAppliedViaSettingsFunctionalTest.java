package name.remal.gradle_plugins.sonarlint;

import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.toolkit.testkit.functional.GradleProject;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SonarLintPluginAppliedViaSettingsFunctionalTest {

    final GradleProject project;

    @Test
    void appliedViaSettingsIsAppliedToProject() {
        project.forSettingsFile(settings -> settings.applyPlugin("name.remal.sonarlint"));

        // The plugin must NOT be applied via the project's build file: it should reach the project
        // solely through the Settings-level application propagating via GradleLifecycle.beforeProject.
        // The check runs at configuration time (not inside a task action), because accessing
        // `Task.project` at execution time is unsupported with the configuration cache, which this
        // test infrastructure always enables.
        project.getBuildFile().line(
            "assert project.pluginManager.hasPlugin('name.remal.sonarlint')"
        );

        project.assertBuildSuccessfully("help");
    }

}
