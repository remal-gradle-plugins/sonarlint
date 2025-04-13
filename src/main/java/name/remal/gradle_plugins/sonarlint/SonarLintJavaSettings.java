package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.toolkit.JavaToolchainServiceUtils.getJavaToolchainToolProviderFor;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

public abstract class SonarLintJavaSettings {

    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<Boolean> getDisableRulesConflictingWithLombok();


    @Nested
    @org.gradle.api.tasks.Optional
    public abstract Property<JavaInstallationMetadata> getJvm();

    {
        getJvm().convention(
            getJavaToolchainToolProviderFor(getProject(), JavaToolchainService::compilerFor)
                .map(JavaCompiler::getMetadata)
        );
    }

    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<JavaLanguageVersion> getRelease();

    {
        getRelease().convention(
            getJvm()
                .map(JavaInstallationMetadata::getLanguageVersion)
        );
    }

    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<Boolean> getEnablePreview();


    @InputFiles
    @org.gradle.api.tasks.Optional
    @PathSensitive(RELATIVE)
    public abstract ConfigurableFileCollection getMainOutputDirectories();

    @InputFiles
    @org.gradle.api.tasks.Optional
    @Classpath
    public abstract ConfigurableFileCollection getMainClasspath();


    @InputFiles
    @org.gradle.api.tasks.Optional
    @PathSensitive(RELATIVE)
    public abstract ConfigurableFileCollection getTestOutputDirectories();

    @InputFiles
    @org.gradle.api.tasks.Optional
    @Classpath
    public abstract ConfigurableFileCollection getTestClasspath();


    @Inject
    protected abstract Project getProject();

}
