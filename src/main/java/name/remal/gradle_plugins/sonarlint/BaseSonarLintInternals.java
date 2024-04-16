package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_NODEJS_EXECUTABLE;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_NODEJS_EXECUTABLE_TS;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_NODEJS_VERSION;
import static name.remal.gradle_plugins.sonarlint.NodeJsVersions.MIN_SUPPORTED_NODEJS_VERSION;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getCodeFormattingPathsFor;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getRootDirOf;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultTrue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.ServiceRegistryUtils.getService;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

import java.io.File;
import javax.inject.Inject;
import lombok.Getter;
import lombok.val;
import name.remal.gradle_plugins.toolkit.FileUtils;
import name.remal.gradle_plugins.toolkit.Version;
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
                task.getLogger().info("Using Node.js from Sonar properties: {}", definedPath);
                return getLayout().getProjectDirectory().file(definedPath);
            }

            if (!defaultTrue(task.getDetectNodeJs().getOrNull())) {
                task.getLogger().info("Node.js detection is disabled");
                return null;
            }

            final File nodeJsExecutable;
            val nodeJsDetectors = getObjects().newInstance(NodeJsDetectors.class, rootDir);
            val expectedVersion = task.getSonarProperties().getting(SONAR_NODEJS_VERSION).getOrNull();
            if (isEmpty(expectedVersion)) {
                task.getLogger().info("Detecting Node.js of any supported version");
                nodeJsExecutable = nodeJsDetectors.detectDefaultNodeJsExecutable();

            } else if (Version.parse(expectedVersion).compareTo(MIN_SUPPORTED_NODEJS_VERSION) < 0) {
                task.getLogger().warn(
                    "Node.js version {} is requested in Sonar properties"
                        + ", which is less than min supported version {}",
                    expectedVersion,
                    MIN_SUPPORTED_NODEJS_VERSION
                );
                task.getLogger().info("Detecting Node.js of any supported version");
                nodeJsExecutable = nodeJsDetectors.detectDefaultNodeJsExecutable();

            } else {
                task.getLogger().info("Detecting Node.js of version {}", expectedVersion);
                nodeJsExecutable = nodeJsDetectors.detectNodeJsExecutable(expectedVersion);
            }

            if (nodeJsExecutable != null) {
                task.getLogger().info("Detected Node.js: {}", nodeJsExecutable);
                return getLayout().getProjectDirectory().file(nodeJsExecutable.getAbsolutePath());
            }

            task.getLogger().info("Node.js couldn't be detected");
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
