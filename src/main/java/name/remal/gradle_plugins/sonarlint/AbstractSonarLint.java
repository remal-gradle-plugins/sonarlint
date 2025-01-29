package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.internal.SonarLintConstants.MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION;
import static org.gradle.api.tasks.PathSensitivity.ABSOLUTE;

import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import java.lang.reflect.ParameterizedType;
import javax.inject.Inject;
import name.remal.gradle_plugins.sonarlint.settings.WithSonarLintForkSettings;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

public abstract class AbstractSonarLint<
    WorkActionParams extends AbstractSonarLintWorkActionParams,
    WorkAction extends AbstractSonarLintWorkAction<WorkActionParams>
    >
    extends DefaultTask
    implements WithSonarLintForkSettings {

    @Internal
    @ForOverride
    @SuppressWarnings("unchecked")
    protected Class<WorkAction> getWorkActionClass() {
        var typeToken = (TypeToken<? extends AbstractSonarLint<?, ?>>) TypeToken.of(this.getClass());
        var superTypeToken = typeToken.getSupertype(AbstractSonarLint.class);
        var type = superTypeToken.getType();
        if (type instanceof ParameterizedType) {
            var parameterizedType = (ParameterizedType) type;
            return (Class<WorkAction>) TypeToken.of(parameterizedType.getActualTypeArguments()[1]).getRawType();
        } else {
            throw new AssertionError("Not a ParameterizedType: " + type);
        }
    }

    @ForOverride
    @OverridingMethodsMustInvokeSuper
    protected void configureWorkActionParams(WorkActionParams workActionParams) {
        workActionParams.getPluginFiles().from(getPluginFiles());
    }


    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getCoreClasspath();

    @InputFiles
    @Classpath
    @PathSensitive(ABSOLUTE)
    @org.gradle.api.tasks.Optional
    public abstract ConfigurableFileCollection getPluginFiles();


    @TaskAction
    public final void execute() {
        var workQueue = createWorkQueue();
        workQueue.submit(getWorkActionClass(), this::configureWorkActionParams);
    }

    protected final WorkQueue createWorkQueue() {
        var forkOptions = getFork();

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

}
