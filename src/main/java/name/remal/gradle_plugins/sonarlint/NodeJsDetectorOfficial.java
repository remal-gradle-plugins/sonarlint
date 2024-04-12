package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.nio.file.Files.newOutputStream;
import static name.remal.gradle_plugins.sonarlint.NodeJsVersions.LATEST_NODEJS_LTS_VERSION;
import static name.remal.gradle_plugins.sonarlint.OsDetector.DETECTED_OS;
import static name.remal.gradle_plugins.toolkit.PathUtils.createParentDirectories;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrow;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.tisonkun.os.core.Arch;
import com.tisonkun.os.core.OS;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository.MetadataSources;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Setter
abstract class NodeJsDetectorOfficial extends NodeJsDetector
    implements NodeJsDetectorWithRootDir {

    private static final String NODEJS_REPOSITORY_NAME = "https://nodejs.org/";
    private static final String NODEJS_GROUP = "org.nodejs";
    private static final String NODEJS_ARTIFACT_ID = "node";

    private static final Map<OS, String> OS_SUFFIXES = ImmutableMap.<OS, String>builder()
        .put(OS.aix, "aix")
        .put(OS.linux, "linux")
        .put(OS.osx, "darwin")
        .put(OS.freebsd, "linux")
        .put(OS.sunos, "sunos")
        .put(OS.windows, "win")
        .build();

    private static final Map<Arch, String> ARCH_SUFFIXES = ImmutableMap.<Arch, String>builder()
        .put(Arch.x86_64, "x64")
        .put(Arch.x86_32, "x86")
        .put(Arch.aarch_64, "arm64")
        .put(Arch.ppc_64, "ppc64")
        .put(Arch.ppcle_64, "ppc64le")
        .put(Arch.s390_64, "s390x")
        .build();


    @Nullable
    private File rootDir;


    @Nullable
    @Override
    public File detectDefaultNodeJsExecutable() {
        return detectNodeJsExecutable(LATEST_NODEJS_LTS_VERSION);
    }

    @Nullable
    @Override
    @SuppressWarnings("java:S3776")
    public File detectNodeJsExecutable(String version) {
        addNodeJsRepository();

        File rootDir = this.rootDir;
        if (rootDir == null) {
            rootDir = getLayout().getProjectDirectory().getAsFile();
        }

        val os = DETECTED_OS.os;
        val arch = DETECTED_OS.arch;
        val targetFile = new File(rootDir, format(
            "build/node-%s-%s-%s%s",
            version,
            os,
            arch,
            os == OS.windows ? ".exe" : ""
        ));
        if (targetFile.exists()) {
            return targetFile;
        }

        val osSuffix = OS_SUFFIXES.get(os);
        if (osSuffix == null) {
            throw new NodeJsDetectorException("Detected OS: " + DETECTED_OS.os);
        }
        val archSuffix = ARCH_SUFFIXES.get(arch);
        if (archSuffix == null) {
            throw new NodeJsDetectorException("Detected architecture: " + DETECTED_OS.arch);
        }
        val archiveExtension = os == OS.windows ? "zip" : "tar.gz";
        val dependency = getDependencies().create(format(
            "%s:%s:%s:%s@%s",
            NODEJS_GROUP,
            NODEJS_ARTIFACT_ID,
            version,
            osSuffix + "-" + archSuffix,
            archiveExtension
        ));
        val configuration = getConfigurations().detachedConfiguration(dependency);
        val archiveFile = configuration.getFiles().iterator().next();

        final FileTree archiveFileTree;
        if (archiveExtension.equals("zip")) {
            archiveFileTree = getArchives().zipTree(archiveFile);
        } else if (archiveExtension.equals("tar.gz")) {
            archiveFileTree = getArchives().tarTree(getArchives().gzip(archiveFile));
        } else {
            throw new NodeJsDetectorException("Unsupported archive extension: " + archiveExtension);
        }
        archiveFileTree
            .matching(filter -> filter.include(
                os == OS.windows ? "*/node.exe" : "*/bin/node"
            ))
            .visit(detail -> {
                if (detail.isDirectory()) {
                    return;
                }

                val targetFilePath = targetFile.toPath();
                createParentDirectories(targetFilePath);
                try (val outputStream = newOutputStream(targetFilePath)) {
                    try (val inputStream = detail.open()) {
                        ByteStreams.copy(inputStream, outputStream);
                    }
                } catch (IOException e) {
                    throw sneakyThrow(e);
                }
            });

        if (!targetFile.exists()) {
            throw new NodeJsDetectorException("Node.js binary couldn't be found in archive: " + archiveFile);
        }

        setExecutePermissions(targetFile);
        checkNodeJsExecutableForTests(targetFile);
        return targetFile;
    }

    private void addNodeJsRepository() {
        if (getRepositories().getNames().contains(NODEJS_REPOSITORY_NAME)) {
            return;
        }

        getRepositories().ivy(repo -> {
            repo.setName(NODEJS_REPOSITORY_NAME);
            repo.setUrl("https://nodejs.org/dist");
            repo.patternLayout(layout -> {
                layout.artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]");
            });
            repo.metadataSources(MetadataSources::artifact);
            repo.content(content -> {
                content.includeModule(NODEJS_GROUP, NODEJS_ARTIFACT_ID);
            });
        });
    }


    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }


    @Inject
    protected abstract RepositoryHandler getRepositories();

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    @Inject
    protected abstract DependencyHandler getDependencies();

    @Inject
    protected abstract ProjectLayout getLayout();

    @Inject
    protected abstract ArchiveOperations getArchives();

}
