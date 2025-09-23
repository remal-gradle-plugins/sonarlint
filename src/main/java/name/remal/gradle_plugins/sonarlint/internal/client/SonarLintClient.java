package name.remal.gradle_plugins.sonarlint.internal.client;

import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.write;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static name.remal.gradle_plugins.build_time_constants.api.BuildTimeConstants.getStringProperty;
import static name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Created.CLIENT_CREATED;
import static name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientState.Stopped.CLIENT_STOPPED;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.connectToRegistry;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.createRegistryOnAvailablePort;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;
import static name.remal.gradle_plugins.toolkit.DebugUtils.isDebugEnabled;
import static name.remal.gradle_plugins.toolkit.GradleVersionUtils.isCurrentGradleVersionLessThan;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyProxy;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursivelyIgnoringFailure;
import static name.remal.gradle_plugins.toolkit.StringUtils.indentString;
import static name.remal.gradle_plugins.toolkit.ThrowableUtils.unwrapException;
import static org.slf4j.event.Level.DEBUG;

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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
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
import name.remal.gradle_plugins.sonarlint.internal.utils.AccumulatingLogger;
import name.remal.gradle_plugins.sonarlint.internal.utils.ServerRegistryFacade;
import name.remal.gradle_plugins.sonarlint.internal.utils.SonarLintRmiMethodCallException;
import name.remal.gradle_plugins.sonarlint.internal.utils.SonarLintServerStartTimeoutException;
import name.remal.gradle_plugins.toolkit.AbstractCloseablesContainer;
import name.remal.gradle_plugins.toolkit.UriUtils;
import org.gradle.api.JavaVersion;
import org.gradle.util.GradleVersion;

@RequiredArgsConstructor
public class SonarLintClient extends AbstractCloseablesContainer implements AutoCloseable {

    private static final Duration START_TIMEOUT = isDebugEnabled() ? Duration.ofMinutes(5) : Duration.ofSeconds(10);


    private final AccumulatingLogger logger = new AccumulatingLogger(SonarLintClient.class);

    {
        registerCloseable(logger::reset);
    }


    private final JavaExec javaExec;

    private final SonarLintClientParams params;

    public SonarLintClient(SonarLintClientParams params) {
        this(new JavaExecDefault(), params);
    }


    private volatile SonarLintClientState state = CLIENT_CREATED;

    private void changeState(SonarLintClientState state) {
        newLoggingEvent(DEBUG).message(
            "Changing state to %s from %s",
            state,
            this.state
        ).log(logger);
        this.state = state;
    }


    @SuppressWarnings("java:S2259")
    private String renderDebugInfo() {
        var buf = new StringBuilder();
        Supplier<StringBuilder> withNewLineIfNeeded = () -> {
            if (buf.length() > 0) {
                buf.append(lineSeparator());
            }
            return buf;
        };

        var state = this.state;

        withNewLineIfNeeded.get()
            .append("State: ").append(state.getClass().getSimpleName());

        withNewLineIfNeeded.get()
            .append("Client Java version: ").append(JavaVersion.current().getMajorVersion());

        withNewLineIfNeeded.get()
            .append("Client logs:").append(lineSeparator())
            .append(indentString(logger.render()).replace("\n", lineSeparator()));

        withNewLineIfNeeded.get()
            .append("Server Java version: ").append(params.getJavaMajorVersion());

        final JavaExecProcess serverProcess;
        if (state instanceof Starting) {
            serverProcess = ((Starting) state).getServerProcess().get();
        } else if (state instanceof Started) {
            serverProcess = ((Started) state).getServerProcess();
        } else {
            serverProcess = null;
        }
        if (serverProcess != null) {
            withNewLineIfNeeded.get()
                .append("Server pid: ").append(serverProcess.getProcess().pid());
            withNewLineIfNeeded.get()
                .append("Server logs file: ").append(serverProcess.getOutputFile());
            withNewLineIfNeeded.get()
                .append("Server logs:")
                .append(lineSeparator())
                .append(indentString(serverProcess.readOutput()).replace("\n", lineSeparator()));
        }

        return buf.toString();
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
                throw new SonarLintServerStartTimeoutException(START_TIMEOUT, renderDebugInfo());
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
                        throw new SonarLintRmiMethodCallException(
                            realMethod,
                            renderDebugInfo(),
                            exception
                        );
                    } finally {
                        close();
                    }
                }

                throw exception;
            }
        });

        stub = logger.wrapCalls(interfaceClass, stub);

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

        if (!startingState.getStartedSignal().await(START_TIMEOUT.toMillis(), MILLISECONDS)) {
            try {
                throw new SonarLintServerStartTimeoutException(START_TIMEOUT, renderDebugInfo());
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

        newLoggingEvent(DEBUG).message(
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

        var serverParamsFile = createTempFile(getClass().getSimpleName() + "-serverParams-", ".params");
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

    @SneakyThrows
    private Collection<File> computeClasspath() {
        Collection<File> classpath = new LinkedHashSet<>();

        classpath.add(getClassJarFile(SonarLintClient.class));

        classpath.addAll(params.getCoreClasspath());

        if (isCurrentGradleVersionLessThan("8.0.9999")) {
            /*
             * Configuration cache for Gradle <=8.0 instruments JAR files of plugins instead of applying Java agent.
             * So, when we use the plugin's JAR in a classpath,
             * there will be references like org.gradle.internal.classpath.Instrumented there.
             */

            var minSupportedVersion = GradleVersion.version(getStringProperty("gradle-api.min-version"));
            var requiredVersion = GradleVersion.version("8.1");
            if (minSupportedVersion.compareTo(requiredVersion) >= 0) {
                throw new AssertionError("Remove this code, as Gradle <=8.0 is no longer supported");
            }

            Stream.of(
                    "org.gradle.internal.classpath.Instrumented",
                    "org.codehaus.groovy.runtime.callsite.CallSite",
                    "org.gradle.api.GradleException"
                )
                .map(className -> {
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(SonarLintClient::getClassJarFile)
                .forEach(classpath::add);
        }

        return classpath;
    }

    private static File getClassJarFile(Class<?> clazz) {
        var classJarFile = Optional.ofNullable(clazz.getProtectionDomain())
            .map(ProtectionDomain::getCodeSource)
            .map(CodeSource::getLocation)
            .map(UriUtils::toUri)
            .map(Paths::get)
            .map(Path::toFile)
            .orElse(null);
        if (classJarFile == null) {
            throw new IllegalStateException(format(
                "Can't determine JAR file of %s",
                clazz
            ));
        }
        return classJarFile;
    }


    @Override
    public synchronized void close() {
        super.close();
    }

}
