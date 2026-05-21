package name.remal.gradle_plugins.sonarlint.internal.server;

import static java.lang.String.format;
import static java.nio.file.Files.readAllBytes;
import static name.remal.gradle_plugins.build_time_constants.api.BuildTimeConstants.getClassPackageName;
import static name.remal.gradle_plugins.build_time_constants.api.BuildTimeConstants.getStringProperty;
import static name.remal.gradle_plugins.sonarlint.internal.utils.JacocoUtils.dumpJacocoData;
import static name.remal.gradle_plugins.sonarlint.internal.utils.LogMessageRenderer.SIMPLE_LOG_MESSAGE_DATE_FORMAT;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.connectToRegistry;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.deserializeFrom;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsRunnable;

import java.nio.file.Paths;
import java.time.Duration;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.SonarLintPlugin;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClient;
import name.remal.gradle_plugins.sonarlint.internal.client.api.SonarLintServerRuntimeInfo;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

public class SonarLintServerMain {

    private static final Duration PARENT_START_INSTANT_TOLERANCE = Duration.ofSeconds(5);

    @SneakyThrows
    public static void main(String[] args) {
        try {
            var serverParamsFile = Paths.get(args[0]);
            var serverParams = deserializeFrom(readAllBytes(serverParamsFile), SonarLintServerParams.class);
            setupLogging(serverParams);
            startServer(serverParams);

        } finally {
            dumpJacocoData();
        }
    }


    private static void setupLogging(SonarLintServerParams serverParams) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();


        System.setProperty(
            "org.slf4j.simpleLogger.defaultLogLevel",
            serverParams.getDefaultLogLevel().name()
        );

        System.setProperty(
            format(
                "org.slf4j.simpleLogger.log.%s",
                getClassPackageName(SonarLintPlugin.class)
            ),
            Level.DEBUG.name()
        );
        System.setProperty(
            format(
                "org.slf4j.simpleLogger.log.%s",
                getClassPackageName(SonarLintServerMain.class)
            ),
            Level.TRACE.name()
        );
        System.setProperty(
            format(
                "org.slf4j.simpleLogger.log.%s",
                getStringProperty("classesRelocation.basePackageForRelocatedClasses")
            ),
            serverParams.getDefaultLogLevel().name()
        );


        System.setProperty(
            "org.slf4j.simpleLogger.showDateTime",
            "true"
        );
        System.setProperty(
            "org.slf4j.simpleLogger.dateTimeFormat",
            SIMPLE_LOG_MESSAGE_DATE_FORMAT
        );
    }


    @SneakyThrows
    private static void startServer(SonarLintServerParams serverParams) {
        var logger = LoggerFactory.getLogger(SonarLintServerMain.class);

        var serverRuntimeInfo = connectToRegistry(
            SonarLintClient.class.getSimpleName(),
            serverParams.getServerRuntimeInfoSocketAddress()
        )
            .lookup(SonarLintServerRuntimeInfo.class);

        try (var server = new SonarLintServer(serverParams)) {
            logger.info("Starting {}", SonarLintServer.class.getSimpleName());
            server.start();


            monitorParentProcessExit(serverParams, () -> stopServer(server));

            logger.info(
                "Reporting {} RMI registry socket address back to the client: {}",
                SonarLintServer.class.getSimpleName(),
                server.getSocketAddress()
            );
            serverRuntimeInfo.reportServerRegistrySocketAddress(server.getSocketAddress());

            logger.info("{} - join", SonarLintServer.class.getSimpleName());
            server.join();

            logger.warn("Exiting");
        }
    }

    private static void stopServer(SonarLintServer server) {
        var logger = LoggerFactory.getLogger(SonarLintServerMain.class);

        logger.info("Stopping server");
        server.close();
        logger.info("Stopped server");
    }

    private static void monitorParentProcessExit(SonarLintServerParams serverParams, Runnable onExit) {
        var logger = LoggerFactory.getLogger(SonarLintServerMain.class);

        var clientPid = serverParams.getClientPid();
        var parentHandle = ProcessHandle.of(clientPid).orElse(null);
        if (parentHandle == null) {
            onExit.run();
            throw new IllegalStateException(format(
                "Parent process already exited: PID %d not found",
                clientPid
            ));
        }

        logger.info("Parent process PID: {}, info: {}", clientPid, parentHandle.info());

        var clientStartInstant = serverParams.getClientStartInstant().orElse(null);
        var currentParentStartInstant = parentHandle.info().startInstant().orElse(null);
        logger.info(
            "Parent process start instant check: recorded={}, current={}",
            clientStartInstant,
            currentParentStartInstant
        );
        if (clientStartInstant != null && currentParentStartInstant != null) {
            var diff = Duration.between(clientStartInstant, currentParentStartInstant).abs();
            if (diff.compareTo(PARENT_START_INSTANT_TOLERANCE) > 0) {
                onExit.run();
                throw new IllegalStateException(format(
                    "Parent process already exited: start instant diff is %s (recorded=%s, current=%s)",
                    diff,
                    clientStartInstant,
                    currentParentStartInstant
                ));
            }
        } else if (currentParentStartInstant == null) {
            logger.warn(
                "Parent process start instant is unavailable (recorded={}, current={}), skipping check",
                clientStartInstant,
                currentParentStartInstant
            );
        }

        var onExitFuture = parentHandle.onExit().thenRun(onExit);
        var onExitMonitor = new Thread(sneakyThrowsRunnable(onExitFuture::get));
        onExitMonitor.setName(SonarLintServerMain.class.getSimpleName() + "-parent-on-exit-monitor");
        onExitMonitor.setDaemon(true);
        onExitMonitor.start();
    }

}
