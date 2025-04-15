package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathFirstLevelLibraryNotations;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathLibraryFilePaths;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.toolkit.testkit.MinSupportedJavaVersion;

@MinSupportedJavaVersion(17)
abstract class AbstractSonarLintServiceComponentTest {

    protected static <
        T extends AbstractSonarLintServiceParams.AbstractSonarLintServiceParamsBuilder<?, ?>
        > T configureParamsBuilderBase(T builder) {
        builder.pluginPaths(getPluginPaths());
        builder.languagesToProcess(List.of(SonarLintLanguage.values()));
        return builder;
    }

    private static Set<Path> getPluginPaths() {
        var scope = "sonar-plugins";
        var notations = getTestClasspathFirstLevelLibraryNotations(scope);
        return notations.stream()
            .map(notation -> getTestClasspathLibraryFilePaths(scope, notation))
            .flatMap(Collection::stream)
            .collect(toImmutableSet());
    }

}
