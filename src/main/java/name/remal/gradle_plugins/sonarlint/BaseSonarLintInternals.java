package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getCodeFormattingPathsFor;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getRootDirOf;
import static name.remal.gradle_plugins.toolkit.ServiceRegistryUtils.getService;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

import javax.inject.Inject;
import lombok.Getter;
import lombok.val;
import name.remal.gradle_plugins.toolkit.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
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

    @Inject
    public BaseSonarLintInternals(BaseSonarLint task) {
        val project = task.getProject();
        getWorkerExecutor().set(getService(project, WorkerExecutor.class));
        getRootDir().set(getRootDirOf(project));
        getProjectDir().set(normalizeFile(project.getProjectDir()));
        getBuildDir().fileProvider(
            project.getLayout().getBuildDirectory()
                .map(Directory::getAsFile)
                .map(FileUtils::normalizeFile)
        );
        getCodeFormattingFiles().setFrom(getCodeFormattingPathsFor(project));
    }

}
