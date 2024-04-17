package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.nio.file.Files.newOutputStream;
import static name.remal.gradle_plugins.sonarlint.NodeJsVersions.DEFAULT_NODEJS_VERSION;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
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
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsDetectorException;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound;
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
    @SneakyThrows
    @SuppressWarnings("java:S3776")
    public NodeJsFound detectDefaultNodeJsExecutable() {
        addNodeJsRepository();

        File rootDir = this.rootDir;
        if (rootDir == null) {
            rootDir = getLayout().getProjectDirectory().getAsFile();
        }

        val os = osDetector.getDetectedOs().os;
        val arch = osDetector.getDetectedOs().arch;
        val version = DEFAULT_NODEJS_VERSION.toString();
        val targetFile = new File(rootDir, format(
            "build/node-%s-%s-%s%s",
            version,
            os,
            arch,
            os == OS.windows ? ".exe" : ""
        ));

        if (!targetFile.exists()) {
            val osSuffix = OS_SUFFIXES.get(os);
            if (osSuffix == null) {
                logger.warn("There is Node.js on the official website for OS {}", os);
                return null;
            }
            val archSuffix = ARCH_SUFFIXES.get(arch);
            if (archSuffix == null) {
                logger.warn("There is Node.js on the official website for OS {} and CPU architecture {}", os, arch);
                return null;
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
        }

        val info = nodeJsInfoRetriever.getNodeJsInfo(targetFile);

        if (info instanceof NodeJsNotFound) {
            val error = ((NodeJsNotFound) info).getError();
            if (isInTest()) {
                throw error;
            } else {
                val message = format(
                    "Downloaded Node.js from the official website of version %s can't be used: %s",
                    version,
                    error
                );
                logger.warn(message, error);
            }
            return null;
        }

        return (NodeJsFound) info;
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
