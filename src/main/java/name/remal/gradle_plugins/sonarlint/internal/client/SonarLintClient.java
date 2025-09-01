package name.remal.gradle_plugins.sonarlint.internal.client;

import static java.lang.String.format;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.write;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Created.CLIENT_CREATED;
import static name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Stopped.CLIENT_STOPPED;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.connectToRegistry;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.createRegistryOnAvailablePort;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;
import static name.remal.gradle_plugins.toolkit.DebugUtils.isDebugEnabled;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyProxy;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursivelyIgnoringFailure;
import static name.remal.gradle_plugins.toolkit.ThrowableUtils.unwrapException;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Created;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Started;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Starting;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Stopped;
import name.remal.gradle_plugins.sonarlint.internal.client.api.SonarLintServerRuntimeInfo;
import name.remal.gradle_plugins.sonarlint.internal.server.ImmutableSonarLintServerParams;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintServerMain;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintHelp;
import name.remal.gradle_plugins.sonarlint.internal.utils.ServerRegistryFacade;
import name.remal.gradle_plugins.toolkit.AbstractCloseablesContainer;
import name.remal.gradle_plugins.toolkit.UriUtils;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
@SuppressWarnings("JavaTimeDefaultTimeZone")
public class SonarLintClient
    extends AbstractCloseablesContainer
    implements AutoCloseable {

    private static final Duration startTimeout = isDebugEnabled() ? Duration.ofMinutes(5) : Duration.ofSeconds(10);

    private static final Logger logger = LoggerFactory.getLogger(SonarLintClient.class);


    private final JavaExec javaExec;

    private final SonarLintClientParams params;

    public SonarLintClient(SonarLintClientParams params) {
        this(new JavaExecDefault(), params);
    }


    private volatile SonarLintClientState state = CLIENT_CREATED;

    private void changeState(SonarLintClientState state) {
        newLoggingEvent(DEBUG, isInTest() ? WARN : null).message(
            "%s: Changing state to %s from %s",
            LocalTime.now(),
            state,
            this.state
        ).log(logger);
        this.state = state;
    }


    @Getter
    private final SonarLintAnalyzer analyzer = asLazyProxy(SonarLintAnalyzer.class, () ->
        startServerAndLookupApi(SonarLintAnalyzer.class)
    );

    @Getter
    private final SonarLintHelp help = asLazyProxy(SonarLintHelp.class, () ->
        startServerAndLookupApi(SonarLintHelp.class)
    );

    @SuppressWarnings("AssignmentToCatchBlockParameter")
    private <T extends Remote> T startServerAndLookupApi(Class<T> interfaceClass) {
        start();

        if (state instanceof Started) {
            // proceed to the logic
        } else if (state instanceof Stopped) {
            throw new IllegalStateException(format(
                "%s has already stopped",
                getClass().getSimpleName()
            ));
        } else {
            throw new UnsupportedOperationException(format(
                "%s unexpected state: %s",
                getClass().getSimpleName(),
                state
            ));
        }

        var state = (Started) this.state;

        T stub;
        try {
            stub = state.getServerRegistry().lookup(interfaceClass);
        } catch (Exception e) {
            try {
                throw new SonarLintClientException(format(
                    "SonarLint server couldn't start within %s."
                        + " Local time: %s."
                        + " Server output:%n%s",
                    startTimeout,
                    LocalTime.now(),
                    state.getServerProcess().readOutput()
                ));
            } finally {
                close();
            }
        }

        stub = withWrappedCalls(interfaceClass, stub, realMethod -> {
            try {
                return realMethod.call();

            } catch (Throwable exception) {
                exception = unwrapException(exception);

                if (exception instanceof RemoteException
                    || exception instanceof NotBoundException
                ) {
                    try {
                        throw new SonarLintClientException(format(
                            "An exception occurred while calling for an RMI stub of %s."
                                + " Local time: %s."
                                + " Server output:%n%s",
                            interfaceClass,
                            LocalTime.now(),
                            state.getServerProcess().readOutput()
                        ));
                    } finally {
                        close();
                    }
                }

                throw exception;
            }
        });

        return stub;
    }


    private final InetAddress loopbackAddress = getLoopbackAddress();

    public InetAddress getBindAddress() {
        return loopbackAddress;
    }


    @SneakyThrows
    @SuppressWarnings("java:S2259")
    private synchronized void start() {
        if (state instanceof Created) {
            // proceed to the logic
        } else if (state instanceof Starting) {
            return; // already starting
        } else if (state instanceof Started) {
            return; // already started
        } else if (state instanceof Stopped) {
            throw new IllegalStateException(format(
                "%s has already stopped",
                getClass().getSimpleName()
            ));
        } else {
            throw new UnsupportedOperationException(format(
                "%s unexpected state: %s",
                getClass().getSimpleName(),
                state
            ));
        }


        registerCloseable(() -> changeState(CLIENT_STOPPED));


        var startingState = Starting.builder()
            .build();
        registerCloseable(() -> startingState.getStartedSignal().countDown());

        changeState(startingState);

        var serverRuntimeInfoRegistry = startServerRuntimeInfoEndpoint();
        var serverProcess = registerCloseable(startServer(serverRuntimeInfoRegistry));
        startingState.getServerProcess().set(serverProcess);

        if (!startingState.getStartedSignal().await(startTimeout.toMillis(), MILLISECONDS)) {
            try {
                throw new SonarLintClientException(format(
                    "SonarLint server couldn't start within %s."
                        + " Local time: %s."
                        + " Server output:%n%s",
                    startTimeout,
                    LocalTime.now(),
                    serverProcess.readOutput()
                ));
            } finally {
                close();
            }
        }
    }

    @SuppressWarnings("java:S2259")
    private void processServerRegistrySocketAddress(InetSocketAddress socketAddress) {
        if (state instanceof Starting) {
            // proceed to the logic
        } else if (state instanceof Stopped) {
            throw new IllegalStateException(format(
                "%s has already stopped",
                getClass().getSimpleName()
            ));
        } else {
            throw new UnsupportedOperationException(format(
                "%s unexpected state: %s",
                getClass().getSimpleName(),
                state
            ));
        }

        newLoggingEvent(DEBUG, isInTest() ? WARN : null).message(
            "Reported server socket address: %s",
            socketAddress
        ).log(logger);

        var serverRegistry = connectToRegistry(
            SonarLintServerMain.class.getSimpleName(),
            socketAddress
        );

        var startingState = (Starting) state;

        changeState(
            Started.builder()
                .serverRegistry(serverRegistry)
                .serverProcess(requireNonNull(startingState.getServerProcess().get()))
                .build()
        );

        startingState.getStartedSignal().countDown();
    }

    private ServerRegistryFacade startServerRuntimeInfoEndpoint() {
        var registry = registerCloseable(createRegistryOnAvailablePort(
            getClass().getSimpleName(),
            loopbackAddress
        ));
        registry.bind(SonarLintServerRuntimeInfo.class, new SonarLintServerRuntimeInfo() {
            @Override
            public synchronized void reportServerRegistrySocketAddress(
                InetSocketAddress socketAddress
            ) throws RemoteException {
                SonarLintClient.this.processServerRegistrySocketAddress(socketAddress);
            }
        });
        return registry;
    }

    @SneakyThrows
    @SuppressWarnings("java:S5443")
    private JavaExecProcess startServer(ServerRegistryFacade serverRuntimeInfoRegistry) {
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
    public synchronized void close() {
        super.close();
    }

}
