package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.toolkit.CiUtils.isRunningOnCiIncludingTests;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.packageNameOf;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.unwrapGeneratedSubclass;
import static name.remal.gradle_plugins.toolkit.testkit.ProjectValidations.executeAfterEvaluateActions;

import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.toolkit.testkit.TaskValidations;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SonarLintPluginTest {

    final Project project;

    @BeforeEach
    void beforeEach() {
        project.getPluginManager().apply(SonarLintPlugin.class);
        var repositories = project.getRepositories();
        if (isRunningOnCiIncludingTests()) {
            repositories.maven(repo -> {
                repo.setName("googleMavenCentralMirror");
                repo.setUrl("https://maven-central.storage-download.googleapis.com/maven2/");
                repo.mavenContent(MavenRepositoryContentDescriptor::releasesOnly);
            });
        }
        repositories.mavenCentral();
    }

    @Test
    void pluginTasksDoNotHavePropertyProblems() {
        project.getPluginManager().apply("java");

        executeAfterEvaluateActions(project);

        var taskClassNamePrefix = packageNameOf(SonarLintPlugin.class) + '.';
        project.getTasks().stream()
            .filter(task -> {
                var taskClass = unwrapGeneratedSubclass(task.getClass());
                return taskClass.getName().startsWith(taskClassNamePrefix);
            })
            .map(TaskValidations::markTaskDependenciesAsSkipped)
            .forEach(TaskValidations::assertNoTaskPropertiesProblems);
    }

}
