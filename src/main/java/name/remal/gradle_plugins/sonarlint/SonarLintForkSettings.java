package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.SonarLintConstants.MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION;
import static name.remal.gradle_plugins.toolkit.DebugUtils.isDebugEnabled;
import static name.remal.gradle_plugins.toolkit.JavaLauncherUtils.getJavaLauncherProviderFor;

import javax.inject.Inject;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;

public abstract class SonarLintForkSettings {

    @Internal
    public abstract Property<Boolean> getEnabled();

    {
        boolean isEnabledByDefault = !isDebugEnabled();
        /*
        if (isInTest()) {
            isEnabledByDefault = false;
        }
        if (getStringProperty("project.version").endsWith("-SNAPSHOT")) {
            isEnabledByDefault = false;
        }
        */
        getEnabled().convention(isEnabledByDefault);
    }


    @Internal
    public abstract Property<String> getMaxHeapSize();


    @Nested
    @org.gradle.api.tasks.Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    {
        getJavaLauncher().convention(getJavaLauncherProviderFor(getProject(), spec -> {
            var minSupportedJavaLanguageVersion = JavaLanguageVersion.of(
                MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION.getMajorVersion()
            );
            var javaMajorVersion = spec.getLanguageVersion()
                .orElse(JavaLanguageVersion.of(JavaVersion.current().getMajorVersion()))
                .map(JavaLanguageVersion::asInt)
                .get();
            if (javaMajorVersion < minSupportedJavaLanguageVersion.asInt()) {
                spec.getLanguageVersion().set(minSupportedJavaLanguageVersion);
            }
        }));
    }


    @Inject
    protected abstract Project getProject();

}
