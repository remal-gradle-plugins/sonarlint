package name.remal.gradle_plugins.sonarlint;

import static com.google.common.collect.Maps.immutableEntry;
import static name.remal.gradle_plugins.sonarlint.AbstractSonarLintTask.collectDependencyChanges;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import name.remal.gradle_plugins.sonarlint.AbstractSonarLintTask.SonarResolvedDependencyId;
import name.remal.gradle_plugins.sonarlint.AbstractSonarLintTask.VersionChange;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AbstractSonarLintTaskTest {

    @Nested
    class CollectDependencyChanges {

        @Test
        void empty() {
            var changes = collectDependencyChanges(
                Set.of(),
                Set.of()
            );

            assertThat(changes.missingDependencies)
                .as("missingDependencies")
                .isEmpty();

            assertThat(changes.changedVersions)
                .as("changedVersions")
                .isEmpty();

            assertThat(changes.unexpectedDependencies)
                .as("unexpectedDependencies")
                .isEmpty();
        }

        @Test
        void missingDependencies() {
            var dep = SonarResolvedDependency.builder()
                .moduleGroup("group")
                .moduleName("name")
                .moduleVersion("1")
                .build();

            var changes = collectDependencyChanges(
                Set.of(dep),
                Set.of()
            );

            assertThat(changes.missingDependencies)
                .as("missingDependencies")
                .containsExactly(dep);

            assertThat(changes.changedVersions)
                .as("changedVersions")
                .containsExactly();

            assertThat(changes.unexpectedDependencies)
                .as("unexpectedDependencies")
                .containsExactly();
        }

        @Test
        void changedVersions() {
            var depV1 = SonarResolvedDependency.builder()
                .moduleGroup("group")
                .moduleName("name")
                .moduleVersion("1")
                .build();

            var depV2 = SonarResolvedDependency.builder()
                .moduleGroup("group")
                .moduleName("name")
                .moduleVersion("2")
                .build();

            var changes = collectDependencyChanges(
                Set.of(depV1),
                Set.of(depV2)
            );

            assertThat(changes.missingDependencies)
                .as("missingDependencies")
                .containsExactly();

            assertThat(changes.changedVersions)
                .as("changedVersions")
                .containsExactly(
                    immutableEntry(
                        new SonarResolvedDependencyId("group", "name"),
                        new VersionChange("1", "2")
                    )
                );

            assertThat(changes.unexpectedDependencies)
                .as("unexpectedDependencies")
                .containsExactly();
        }

        @Test
        void unexpectedDependencies() {
            var dep = SonarResolvedDependency.builder()
                .moduleGroup("group")
                .moduleName("name")
                .moduleVersion("1")
                .build();

            var changes = collectDependencyChanges(
                Set.of(),
                Set.of(dep)
            );

            assertThat(changes.missingDependencies)
                .as("missingDependencies")
                .containsExactly();

            assertThat(changes.changedVersions)
                .as("changedVersions")
                .containsExactly();

            assertThat(changes.unexpectedDependencies)
                .as("unexpectedDependencies")
                .containsExactly(dep);
        }

    }

}
