package name.remal.gradle_plugins.sonarlint.communication.client;

import static java.lang.String.format;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.write;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toUnmodifiableList;
import static lombok.AccessLevel.NONE;
import static name.remal.gradle_plugins.sonarlint.communication.utils.RegistryFactory.createRegistryOnAvailablePort;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.communication.client.api.SonarLintServerRuntimeInfo;
import name.remal.gradle_plugins.sonarlint.communication.server.SonarLintServerMain;
import name.remal.gradle_plugins.sonarlint.communication.shared.ImmutableSonarLintServerParams;
import name.remal.gradle_plugins.sonarlint.communication.utils.ServerRegistryFacade;
import name.remal.gradle_plugins.toolkit.ClosablesContainer;
import name.remal.gradle_plugins.toolkit.UriUtils;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

@Builder
public class SonarLintClient {

    private final JavaExec javaExec;

    private final File javaExecutable;

    @Singular("coreClasspathFile")
    private final Set<File> coreClasspath;

    @Nullable
    private final String maxHeapSize;


    @Singular
    private final Set<File> pluginFiles;

    @Singular
    private final Set<SonarLintLanguage> enabledPluginLanguages;


    private final InetAddress loopbackAddress = getLoopbackAddress();
    private final AtomicReference<@Nullable InetSocketAddress> serverRegistrySocketAddress = new AtomicReference<>();
    private final CountDownLatch startSignal = new CountDownLatch(1);
    private static final Duration startTimeout = Duration.ofSeconds(5);

    @SneakyThrows
    public void start() {
        if (serverRegistrySocketAddress.get() != null) {
            throw new IllegalStateException("Already started");
        }

        var serverRuntimeInfoRegistry = startServerRuntimeInfoEndpoint();
        startServer(serverRuntimeInfoRegistry);

        if (!startSignal.await(startTimeout.toMillis(), MILLISECONDS)) {
            throw new SonarLintClientException(format(
                "SonarLint server couldn't start within %s",
                startTimeout
            ));
        }
    }

    private ServerRegistryFacade startServerRuntimeInfoEndpoint() {
        var serverRuntimeInfo = new SonarLintServerRuntimeInfo() {
            @Override
            public void reportServerRegistrySocketAddress(
                InetSocketAddress socketAddress
            ) throws RemoteException {
                serverRegistrySocketAddress.set(socketAddress);
                closeables.registerCloseable(() -> serverRegistrySocketAddress.set(null));
                startSignal.countDown();
            }
        };
        var registry = closeables.registerCloseable(createRegistryOnAvailablePort(loopbackAddress));
        registry.bind(SonarLintServerRuntimeInfo.class, serverRuntimeInfo);
        return registry;
    }

    @SneakyThrows
    private void startServer(ServerRegistryFacade serverRuntimeInfoRegistry) {
        var serverParams = ImmutableSonarLintServerParams.builder()
            .loopbackAddress(loopbackAddress)
            .clientPid(ProcessHandle.current().pid())
            .clientStartInstant(ProcessHandle.current().info().startInstant())
            .serverRuntimeInfoSocketAddress(serverRuntimeInfoRegistry.getSocketAddress())
            .pluginFiles(pluginFiles)
            .enabledPluginLanguages(enabledPluginLanguages)
            .build();

        var serverParamsFile = createTempFile(SonarLintClient.class.getName() + "-serverParams-", ".bin");
        closeables.registerCloseable(() -> {
            if (tryToDeleteRecursively(serverParamsFile)) {
                // ignore failure
            }
        });
        write(serverParamsFile, serializeToBytes(serverParams));

        javaExec.execute(ImmutableJavaExecParams.builder()
            .executable(javaExecutable)
            .classpath(computeClasspath())
            .maxHeapSize(maxHeapSize)
            .mainClass(SonarLintServerMain.class.getName())
            .arguments(List.of(serverParamsFile.toString()))
            .build()
        );
    }

    @Unmodifiable
    private List<File> computeClasspath() {
        var currentJarClass = SonarLintClient.class;
        var currentJarFile = Optional.ofNullable(currentJarClass.getProtectionDomain())
            .map(ProtectionDomain::getCodeSource)
            .map(CodeSource::getLocation)
            .map(UriUtils::toUri)
            .map(Paths::get)
            .map(Path::toFile)
            .orElse(null);
        if (currentJarFile == null) {
            throw new IllegalStateException(format(
                "Can't determine JAR file of %s",
                currentJarClass
            ));
        }

        return Stream.of(List.of(currentJarFile), coreClasspath)
            .flatMap(Collection::stream)
            .distinct()
            .collect(toUnmodifiableList());
    }


    @Getter(NONE)
    private final ClosablesContainer closeables = new ClosablesContainer();

    public void stop() {
        closeables.close();
    }

}
