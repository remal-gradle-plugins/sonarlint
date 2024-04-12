package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_NODEJS_EXECUTABLE;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_NODEJS_EXECUTABLE_TS;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_NODEJS_VERSION;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getCodeFormattingPathsFor;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getRootDirOf;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.ServiceRegistryUtils.getService;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

import javax.inject.Inject;
import lombok.Getter;
import lombok.val;
import name.remal.gradle_plugins.toolkit.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.workers.WorkerExecutor;

@Getter
abstract class BaseSonarLintInternals {

    @Internal
    public abstract Property<WorkerExecutor> getWorkerExecutor();

    @Internal
    public abstract DirectoryProperty getRootDir();

    @Internal
    public abstract DirectoryProperty getProjectDir();

    @Internal
    public abstract DirectoryProperty getBuildDir();

    @org.gradle.api.tasks.Optional
    @InputFiles
    @PathSensitive(RELATIVE)
    public abstract ConfigurableFileCollection getCodeFormattingFiles();

    //@org.gradle.api.tasks.Optional
    //@InputFile
    //@PathSensitive(ABSOLUTE)
    @Internal
    public abstract RegularFileProperty getNodeJsExecutable();


    @Inject
    @SuppressWarnings("java:S5993")
    public BaseSonarLintInternals(BaseSonarLint task) {
        val project = task.getProject();
        val rootDir = getRootDirOf(project);

        getWorkerExecutor().set(getService(project, WorkerExecutor.class));
        getRootDir().set(rootDir);
        getProjectDir().set(normalizeFile(project.getProjectDir()));
        getBuildDir().fileProvider(
            project.getLayout().getBuildDirectory()
                .map(Directory::getAsFile)
                .map(FileUtils::normalizeFile)
        );
        getCodeFormattingFiles().setFrom(getCodeFormattingPathsFor(project));

        getNodeJsExecutable().convention(getProviders().provider(() -> {
            String definedPath = task.getSonarProperties().getting(SONAR_NODEJS_EXECUTABLE).getOrNull();
            if (isEmpty(definedPath)) {
                definedPath = task.getSonarProperties().getting(SONAR_NODEJS_EXECUTABLE_TS).getOrNull();
            }
            if (isNotEmpty(definedPath)) {
                return getLayout().getProjectDirectory().file(definedPath);
            }

            val nodeJsDetectors = getObjects().newInstance(NodeJsDetectors.class, rootDir);
            val version = task.getSonarProperties().getting(SONAR_NODEJS_VERSION).getOrNull();
            task.getLogger().debug("Detecting Node.js for version {}", version);
            val nodeJsExecutable = isEmpty(version)
                ? nodeJsDetectors.detectDefaultNodeJsExecutable()
                : nodeJsDetectors.detectNodeJsExecutable(version);
            task.getLogger().debug("Detected Node.js for version {}: {}", version, nodeJsExecutable);
            if (nodeJsExecutable != null) {
                return getLayout().getProjectDirectory().file(nodeJsExecutable.getAbsolutePath());
            }

            return null;
        }));
    }


    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ProjectLayout getLayout();

}
