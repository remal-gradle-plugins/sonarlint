package name.remal.gradle_plugins.sonarlint.internal.server;

import static java.lang.String.format;
import static name.remal.gradle_plugins.sonarlint.internal.server.SonarLintServerState.Created.SERVER_CREATED;
import static name.remal.gradle_plugins.sonarlint.internal.server.SonarLintServerState.Stopped.SERVER_STOPPED;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.createRegistryOnAvailablePort;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SonarLintServerException.withServerExceptionCalls;
import static org.slf4j.event.Level.DEBUG;

import java.net.InetSocketAddress;
import java.util.concurrent.Phaser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintServerState.Created;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintServerState.Started;
import name.remal.gradle_plugins.sonarlint.internal.server.SonarLintServerState.Stopped;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintHelp;
import name.remal.gradle_plugins.toolkit.AbstractCloseablesContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
@SuppressWarnings("JavaTimeDefaultTimeZone")
public class SonarLintServer
    extends AbstractCloseablesContainer
    implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SonarLintServer.class);


    private final SonarLintServerParams params;


    private volatile SonarLintServerState state = SERVER_CREATED;

    private void changeState(SonarLintServerState state) {
        newLoggingEvent(DEBUG).message(
            "Changing state to %s from %s",
            state,
            this.state
        ).log(logger);
        this.state = state;
    }


    private final Phaser stopPhaser = new Phaser(1);


    @SneakyThrows
    public synchronized void start() {
        if (state instanceof Created) {
            // proceed to the logic
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


        logger.info("Starting {} at {}", getClass().getSimpleName(), params.getLoopbackAddress());


        registerCloseable(() -> changeState(SERVER_STOPPED));


        var registry = registerCloseable(createRegistryOnAvailablePort(
            getClass().getSimpleName(),
            params.getLoopbackAddress()
        ));


        var usedThreads = new UsedThreads();
        registerCloseable(() -> usedThreads.getUsedThreads().forEach(Thread::interrupt));


        var sonarLintParams = ImmutableSonarLintParams.builder()
            .from(params)
            .build();
        var shared = registerCloseable(new SonarLintSharedCode(sonarLintParams));

        {
            SonarLintAnalyzer analyzer = new SonarLintAnalyzerDefault(shared);
            analyzer = usedThreads.withRegisterThreadEveryCall(SonarLintAnalyzer.class, analyzer);
            analyzer = withServerExceptionCalls(SonarLintAnalyzer.class, analyzer);
            registry.bind(SonarLintAnalyzer.class, analyzer);
        }

        {
            SonarLintHelp help = new SonarLintHelpDefault(shared);
            help = usedThreads.withRegisterThreadEveryCall(SonarLintHelp.class, help);
            help = withServerExceptionCalls(SonarLintHelp.class, help);
            registry.bind(SonarLintHelp.class, help);
        }


        changeState(
            Started.builder()
                .socketAddress(registry.getSocketAddress())
                .build()
        );
    }

    public synchronized InetSocketAddress getSocketAddress() {
        if (state instanceof Created) {
            throw new IllegalStateException(format(
                "%s has NOT started",
                getClass().getSimpleName()
            ));
        } else if (state instanceof Started) {
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
        return state.getSocketAddress();
    }

    public void join() {
        if (state instanceof Created) {
            throw new IllegalStateException(format(
                "%s has NOT started",
                getClass().getSimpleName()
            ));
        } else if (state instanceof Started) {
            // proceed to the logic
        } else if (state instanceof Stopped) {
            return; // already stopped
        } else {
            throw new UnsupportedOperationException(format(
                "%s unexpected state: %s",
                getClass().getSimpleName(),
                state
            ));
        }

        stopPhaser.awaitAdvance(stopPhaser.getPhase());
    }

    @Override
    public synchronized void close() {
        try {
            logger.info("Stopping {}", getClass().getSimpleName());
            super.close();

        } finally {
            stopPhaser.arrive();
        }
    }

}
