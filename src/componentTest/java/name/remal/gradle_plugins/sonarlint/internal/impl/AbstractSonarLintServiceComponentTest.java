package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static name.remal.gradle_plugins.sonarlint.ComponentTestConstants.CURRENT_MINOR_GRADLE_VERSION;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathFirstLevelLibraryNotations;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathLibraryFilePaths;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.impl.AbstractSonarLintServiceParams.AbstractSonarLintServiceParamsBuilder;
import name.remal.gradle_plugins.toolkit.testkit.MinTestableGradleVersion;
import name.remal.gradle_plugins.toolkit.testkit.MinTestableJavaVersion;

@MinTestableJavaVersion(17)
@MinTestableGradleVersion(CURRENT_MINOR_GRADLE_VERSION)
abstract class AbstractSonarLintServiceComponentTest {

    protected static <T extends AbstractSonarLintServiceParamsBuilder<?, ?>> T configureParamsBuilderBase(T builder) {
        builder.pluginFiles(getPluginFiles());
        builder.languagesToProcess(List.of(SonarLintLanguage.values()));
        return builder;
    }

    private static Set<File> getPluginFiles() {
        var scope = "sonar-plugins";
        var notations = getTestClasspathFirstLevelLibraryNotations(scope);
        return notations.stream()
            .map(notation -> getTestClasspathLibraryFilePaths(scope, notation))
            .flatMap(Collection::stream)
            .map(Path::toFile)
            .collect(toImmutableSet());
    }

}
