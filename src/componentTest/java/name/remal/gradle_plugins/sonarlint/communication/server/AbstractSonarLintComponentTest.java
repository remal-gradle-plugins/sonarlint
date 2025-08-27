package name.remal.gradle_plugins.sonarlint.communication.server;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static name.remal.gradle_plugins.sonarlint.TestConstants.CURRENT_MINOR_GRADLE_VERSION;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsFunction;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathFirstLevelLibraryNotations;
import static name.remal.gradle_plugins.toolkit.testkit.TestClasspath.getTestClasspathLibraryFilePaths;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.testkit.MinTestableGradleVersion;
import name.remal.gradle_plugins.toolkit.testkit.MinTestableJavaVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;

@Execution(CONCURRENT)
@MinTestableJavaVersion(17)
@MinTestableGradleVersion(CURRENT_MINOR_GRADLE_VERSION)
abstract class AbstractSonarLintComponentTest<T> {

    @ForOverride
    protected abstract T createInstance(SonarLintSharedCode shared);


    protected T instance;

    @BeforeEach
    @OverridingMethodsMustInvokeSuper
    @SuppressWarnings("unchecked")
    protected void beforeEach(TestInfo testInfo) {
        var rootTestClass = getRootTestClass(testInfo);
        instance = (T) instancesCache.computeIfAbsent(rootTestClass, sneakyThrowsFunction(testClass -> {
            var obj = createInstance(shared.get());
            return obj;
        }));
    }

    private static final ConcurrentMap<Class<?>, Object> instancesCache = new ConcurrentHashMap<>();

    @AfterAll
    static void cleanupInstancesCache(TestInfo testInfo) {
        var rootTestClass = getRootTestClass(testInfo);
        instancesCache.remove(rootTestClass);
    }

    private final LazyValue<SonarLintSharedCode> shared = lazyValue(() -> {
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


    private static Class<?> getRootTestClass(TestInfo testInfo) {
        var testClass = testInfo.getTestClass().orElseThrow();
        while (testClass.getDeclaringClass() != null) {
            testClass = testClass.getDeclaringClass();
        }
        return testClass;
    }

}
