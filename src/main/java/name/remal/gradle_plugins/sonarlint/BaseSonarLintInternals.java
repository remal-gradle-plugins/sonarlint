package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_LIST_PROPERTY_DELIMITER;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getCodeFormattingPathsFor;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getRootDirOf;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultFalse;
import static name.remal.gradle_plugins.toolkit.ServiceRegistryUtils.getService;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrow;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

import com.google.common.base.Splitter;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.Getter;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound;
import name.remal.gradle_plugins.sonarlint.internal.SonarLanguage;
import name.remal.gradle_plugins.toolkit.FileUtils;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RelativePath;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SourceTask;
import org.gradle.workers.WorkerExecutor;

@Getter
@CustomLog
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

    @Internal
    public abstract SetProperty<RelativePath> getRelativePathsRequiringNodeJs();

    @org.gradle.api.tasks.Optional
    @Nested
    public abstract Property<NodeJsFound> getConfiguredNodeJsInfo();

    @org.gradle.api.tasks.Optional
    @Nested
    public abstract Property<NodeJsFound> getDetectedNodeJsInfo();


    @Inject
    @SuppressWarnings({"java:S5993", "java:S3776"})
    public BaseSonarLintInternals(BaseSonarLint task) {
        var project = task.getProject();
        var rootDir = getRootDirOf(project);

        getWorkerExecutor().set(getService(project, WorkerExecutor.class));
        getRootDir().set(rootDir);
        getProjectDir().set(normalizeFile(project.getProjectDir()));
        getBuildDir().fileProvider(
            project.getLayout().getBuildDirectory()
                .map(Directory::getAsFile)
                .map(FileUtils::normalizeFile)
        );
        getCodeFormattingFiles().setFrom(getCodeFormattingPathsFor(project));

        getRelativePathsRequiringNodeJs().set(getProviders().provider(() ->
            calculateRelativePathsRequiringNodeJs(task)
        ));
        getRelativePathsRequiringNodeJs().finalizeValueOnRead();

        getConfiguredNodeJsInfo().set(getProviders().provider(() ->
            calculateConfiguredNodeJsInfo(task, getObjects())
        ));
        getConfiguredNodeJsInfo().finalizeValueOnRead();

        getDetectedNodeJsInfo().set(getProviders().provider(() ->
            calculateDetectedNodeJsInfo(task, rootDir, getObjects())
        ));
        getDetectedNodeJsInfo().finalizeValueOnRead();
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private static Collection<RelativePath> calculateRelativePathsRequiringNodeJs(BaseSonarLint task) {
        if (!(task instanceof SourceTask)) {
            return emptyList();
        }

        var result = new LinkedHashSet<RelativePath>();
        var fileRequiringNodeJsSuffixes = calculateRequiringNodeJsFileSuffixes(task);
        task.getLogger().info("Suffixes of files requiring Node.js: {}", fileRequiringNodeJsSuffixes);
        ((SourceTask) task).getSource().visit(details -> {
            if (details.isDirectory()) {
                return;
            }

            var hasRequiringNodeJsSuffix = fileRequiringNodeJsSuffixes.stream()
                .anyMatch(details.getName()::endsWith);
            if (hasRequiringNodeJsSuffix) {
                result.add(details.getRelativePath());
            }
        });
        return result;
    }

    private static List<String> calculateRequiringNodeJsFileSuffixes(BaseSonarLint task) {
        var sonarProperties = task.getSonarProperties().get();
        return stream(SonarLanguage.values())
            .filter(SonarLanguage::isRequireNodeJs)
            .map(lang -> Optional.ofNullable(lang.getFileSuffixesPropKey())
                .map(sonarProperties::get)
                .filter(ObjectUtils::isNotEmpty)
                .map(value -> Splitter.on(SONAR_LIST_PROPERTY_DELIMITER).splitToStream(value)
                    .map(String::trim)
                    .filter(ObjectUtils::isNotEmpty)
                    .collect(toList())
                )
                .orElse(lang.getDefaultFileSuffixes())
            )
            .flatMap(Collection::stream)
            .distinct()
            .collect(toList());
    }

    @Nullable
    @SuppressWarnings("Slf4jFormatShouldBeConst")
    private static NodeJsFound calculateConfiguredNodeJsInfo(BaseSonarLint task, ObjectFactory objects) {
        var configuredNodeJsExecutable = task.getNodeJs()
            .flatMap(SonarLintNodeJs::getNodeJsExecutable)
            .map(RegularFile::getAsFile)
            .map(FileUtils::normalizeFile)
            .getOrNull();
        if (configuredNodeJsExecutable == null) {
            return null;
        }

        task.getLogger().info("Configured Node.js: {}", configuredNodeJsExecutable);
        var nodeJsInfoRetriever = objects.newInstance(NodeJsInfoRetriever.class);
        var info = nodeJsInfoRetriever.getNodeJsInfo(configuredNodeJsExecutable);
        if (info instanceof NodeJsNotFound) {
            var error = ((NodeJsNotFound) info).getError();
            if (isInTest()) {
                throw sneakyThrow(error);
            } else {
                var message = format(
                    "Configured Node.js (%s) can't be used: %s",
                    configuredNodeJsExecutable,
                    error
                );
                task.getLogger().warn(message, error);
            }
            return null;
        }
        return (NodeJsFound) info;
    }

    @Nullable
    private static NodeJsFound calculateDetectedNodeJsInfo(
        BaseSonarLint task,
        File rootDir,
        ObjectFactory objects
    ) {
        if (!(task instanceof SourceTask)) {
            task.getLogger().debug("{} task does not use Node.js detection", task);
            return null;
        }

        var detectNodeJs = defaultFalse(task.getNodeJs()
            .flatMap(SonarLintNodeJs::getDetectNodeJs)
            .getOrNull()
        );
        if (!detectNodeJs) {
            task.getLogger().info("Node.js detection is disabled");
            return null;
        }

        var nodeJsDetectors = objects.newInstance(NodeJsDetectors.class, rootDir);
        task.getLogger().info("Detecting Node.js of any supported version");
        var nodeJsInfo = nodeJsDetectors.detectNodeJsExecutable();
        if (nodeJsInfo != null) {
            task.getLogger().info("Detected Node.js: {}", nodeJsInfo);
            return nodeJsInfo;
        }

        task.getLogger().info("Node.js couldn't be detected");
        return null;
    }


    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProviderFactory getProviders();

}
