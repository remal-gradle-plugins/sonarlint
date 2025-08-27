package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.SonarLintConstants.MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION;

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public abstract class AbstractSonarLintTask
    extends DefaultTask {

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getCoreClasspath();

    @InputFiles
    @Classpath
    @org.gradle.api.tasks.Optional
    public abstract ConfigurableFileCollection getPluginFiles();

    @Nested
    public abstract SonarLintSettings getSettings();

    @Nested
    protected abstract SonarLintLanguagesSettings getLanguages();


    protected final WorkQueue createWorkQueue() {
        var forkOptions = getSettings().getFork();

        boolean isForkEnabled = forkOptions.getEnabled().get();
        if (!isForkEnabled
            && JavaVersion.current().compareTo(MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION) < 0
        ) {
            getLogger().warn(
                "The current Java version ({}) is less than required for SonarLint {}. Enabling forking for task {}.",
                JavaVersion.current().getMajorVersion(),
                MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION.getMajorVersion(),
                getPath()
            );
            isForkEnabled = true;
        }

        if (isForkEnabled) {
            return getWorkerExecutor().processIsolation(spec -> {
                var javaLauncher = forkOptions.getJavaLauncher().get();
                spec.getForkOptions().setExecutable(javaLauncher.getExecutablePath().getAsFile().getAbsolutePath());
                if (javaLauncher.getMetadata().getLanguageVersion().canCompileOrRun(9)) {
                    spec.getForkOptions().jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED");
                }
                spec.getForkOptions().setMaxHeapSize(forkOptions.getMaxHeapSize().getOrNull());

                spec.getClasspath().from(getCoreClasspath());
            });

        } else {
            return getWorkerExecutor().classLoaderIsolation(spec -> {
                spec.getClasspath().from(getCoreClasspath());
            });
        }
    }

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

}
