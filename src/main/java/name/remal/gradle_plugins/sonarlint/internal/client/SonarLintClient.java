package name.remal.gradle_plugins.sonarlint.internal.client;

import static java.lang.String.format;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.write;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.connectToRegistry;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.createRegistryOnAvailablePort;
import static name.remal.gradle_plugins.toolkit.DebugUtils.isDebugEnabled;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyProxy;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursivelyIgnoringFailure;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.client.api.SonarLintServerRuntimeInfo;
import name.remal.gradle_plugins.sonarlint.internal.server.ImmutableSonarLintServerParams;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintServerMain;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintHelp;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintLifecycle;
import name.remal.gradle_plugins.sonarlint.internal.utils.ClientRegistryFacade;
import name.remal.gradle_plugins.sonarlint.internal.utils.ServerRegistryFacade;
import name.remal.gradle_plugins.toolkit.AbstractCloseablesContainer;
import name.remal.gradle_plugins.toolkit.UriUtils;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class SonarLintClient
    extends AbstractCloseablesContainer
    implements AutoCloseable {

    private final JavaExec javaExec;

    private final SonarLintClientParams params;

    public SonarLintClient(SonarLintClientParams params) {
        this(new JavaExecDefault(), params);
    }


    @Getter
    private final SonarLintAnalyzer analyzer = asLazyProxy(SonarLintAnalyzer.class, () ->
        startAndGetServerRegistry().lookup(SonarLintAnalyzer.class)
    );

    @Getter
    private final SonarLintHelp help = asLazyProxy(SonarLintHelp.class, () ->
        startAndGetServerRegistry().lookup(SonarLintHelp.class)
    );

    private final AtomicReference<@Nullable ClientRegistryFacade> serverRegistry = new AtomicReference<>();

    private ClientRegistryFacade startAndGetServerRegistry() {
        start();

        var serverRegistry = this.serverRegistry.get();
        if (serverRegistry == null) {
            throw new IllegalStateException("Not started");
        }
        return serverRegistry;
    }


    public InetAddress getBindAddress() {
        return loopbackAddress;
    }


    private final InetAddress loopbackAddress = getLoopbackAddress();
    private final AtomicReference<@Nullable InetSocketAddress> serverRegistrySocketAddress = new AtomicReference<>();
    private final CountDownLatch serverStartedSignal = new CountDownLatch(1);
    private static final Duration startTimeout = isDebugEnabled() ? Duration.ofMinutes(5) : Duration.ofSeconds(10);
    private final AtomicBoolean stopped = new AtomicBoolean();

    @SneakyThrows
    private synchronized void start() {
        if (stopped.get()) {
            throw new IllegalStateException(SonarLintClient.class.getSimpleName() + " has already stopped");
        }
        if (serverRegistrySocketAddress.get() != null) {
            // already started
            return;
        }

        var serverRuntimeInfoRegistry = startServerRuntimeInfoEndpoint();
        var execResult = registerCloseable(startServer(serverRuntimeInfoRegistry));

        if (!serverStartedSignal.await(startTimeout.toMillis(), MILLISECONDS)) {
            try {
                throw new SonarLintClientException(format(
                    "SonarLint server couldn't start within %s. Server output:%n%s",
                    startTimeout,
                    execResult.readOutput()
                ));
            } finally {
                close();
            }
        }

        var serverRegistry = connectToRegistry(requireNonNull(serverRegistrySocketAddress.get()));
        this.serverRegistry.set(serverRegistry);
    }

    private ServerRegistryFacade startServerRuntimeInfoEndpoint() {
        var serverRuntimeInfo = new SonarLintServerRuntimeInfo() {
            @Override
            public void reportServerRegistrySocketAddress(InetSocketAddress socketAddress) throws RemoteException {
                if (stopped.get()) {
                    throw new IllegalStateException(SonarLintClient.class.getSimpleName() + " has already stopped");
                }

                serverRegistrySocketAddress.set(socketAddress);
                serverStartedSignal.countDown();
            }
        };
        var registry = registerCloseable(createRegistryOnAvailablePort(loopbackAddress));
        registry.bind(SonarLintServerRuntimeInfo.class, serverRuntimeInfo);
        return registry;
    }

    @SneakyThrows
    @SuppressWarnings("java:S5443")
    private JavaExecResult startServer(ServerRegistryFacade serverRuntimeInfoRegistry) {
        var serverParams = ImmutableSonarLintServerParams.builder()
            .from(params)
            .loopbackAddress(loopbackAddress)
            .clientPid(ProcessHandle.current().pid())
            .clientStartInstant(ProcessHandle.current().info().startInstant())
            .serverRuntimeInfoSocketAddress(serverRuntimeInfoRegistry.getSocketAddress())
            .build();

        var serverParamsFile = createTempFile(getClass().getSimpleName() + "-serverParams-", ".bin");
        registerCloseable(() -> tryToDeleteRecursivelyIgnoringFailure(serverParamsFile));
        write(serverParamsFile, serializeToBytes(serverParams));

        return javaExec.execute(ImmutableJavaExecParams.builder()
            .executable(params.getJavaExecutable())
            .classpath(computeClasspath())
            .maxHeapSize(params.getMaxHeapSize())
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

        return Stream.of(List.of(currentJarFile), params.getCoreClasspath())
            .flatMap(Collection::stream)
            .distinct()
            .collect(toUnmodifiableList());
    }


    @Override
    @SneakyThrows
    public synchronized void close() {
        if (!this.stopped.compareAndSet(false, true)) {
            // already stopped
            return;
        }

        try {
            var serverRegistry = this.serverRegistry.get();
            if (serverRegistry == null) {
                // not started
                return;
            }

            try {
                serverRegistry.lookup(SonarLintLifecycle.class).stop();
            } catch (NotBoundException | RemoteException e) {
                // do nothing
            }

        } finally {
            this.serverRegistrySocketAddress.set(null);
            this.serverRegistry.set(null);
            super.close();
        }
    }

}
