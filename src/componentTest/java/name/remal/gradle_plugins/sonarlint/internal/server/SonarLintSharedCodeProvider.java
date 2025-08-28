package name.remal.gradle_plugins.sonarlint.internal.server;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathFirstLevelLibraryNotations;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathLibraryFilePaths;

import com.google.auto.service.AutoService;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.toolkit.LazyValue;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

@AutoService(TestExecutionListener.class)
public class SonarLintSharedCodeProvider implements TestExecutionListener {

    public static SonarLintSharedCode getSonarLintSharedCode() {
        return INSTANCE.get();
    }

    private static final LazyValue<SonarLintSharedCode> INSTANCE = lazyValue(() -> {
        var shared = new SonarLintSharedCode(createSonarLintParams());
        Runtime.getRuntime().addShutdownHook(new Thread(shared::close));
        return shared;
    });

    private static SonarLintParams createSonarLintParams() {
        return ImmutableSonarLintParams.builder()
            .pluginFiles(getPluginFiles())
            .enabledPluginLanguages(Set.of(SonarLintLanguage.values()))
            .build();
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

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (INSTANCE.isInitialized()) {
            INSTANCE.get().close();
        }
    }

}
