package name.remal.gradle_plugins.sonarlint.internal.server;

import static name.remal.gradle_plugins.sonarlint.TestConstants.CURRENT_MINOR_GRADLE_VERSION;
import static name.remal.gradle_plugins.sonarlint.internal.server.SonarLintSharedCodeProvider.getSonarLintSharedCode;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsFunction;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
            var shared = getSonarLintSharedCode();
            return createInstance(shared);
        }));
    }

    private static final ConcurrentMap<Class<?>, Object> instancesCache = new ConcurrentHashMap<>();

    @AfterAll
    static void cleanupInstancesCache(TestInfo testInfo) {
        var rootTestClass = getRootTestClass(testInfo);
        instancesCache.remove(rootTestClass);
    }


    private static Class<?> getRootTestClass(TestInfo testInfo) {
        var testClass = testInfo.getTestClass().orElseThrow();
        while (testClass.getDeclaringClass() != null) {
            testClass = testClass.getDeclaringClass();
        }
        return testClass;
    }

}
