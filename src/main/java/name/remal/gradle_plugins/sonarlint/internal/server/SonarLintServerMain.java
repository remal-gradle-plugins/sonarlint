package name.remal.gradle_plugins.sonarlint.internal.server;

import static java.lang.String.format;
import static java.nio.file.Files.readAllBytes;
import static name.remal.gradle_plugins.build_time_constants.api.BuildTimeConstants.getClassPackageName;
import static name.remal.gradle_plugins.sonarlint.internal.utils.JacocoUtils.dumpJacocoData;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.connectToRegistry;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.deserializeFrom;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsRunnable;

import java.nio.file.Paths;
import java.util.Objects;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.SonarLintPlugin;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClient;
import name.remal.gradle_plugins.sonarlint.internal.client.api.SonarLintServerRuntimeInfo;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class SonarLintServerMain {

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
        System.setProperty(
            "org.slf4j.simpleLogger.defaultLogLevel",
            serverParams.getDefaultLogLevel().name()
        );

        System.setProperty(
            format(
                "org.slf4j.simpleLogger.log.%s",
                getClassPackageName(SonarLintPlugin.class)
            ),
            isInTest() ? Level.TRACE.name() : Level.DEBUG.name()
        );
        System.setProperty(
            format(
                "org.slf4j.simpleLogger.log.%s.internal._relocated",
                getClassPackageName(SonarLintPlugin.class)
            ),
            serverParams.getDefaultLogLevel().name()
        );

        System.setProperty(
            "org.slf4j.simpleLogger.showDateTime",
            "true"
        );
        System.setProperty(
            "org.slf4j.simpleLogger.dateTimeFormat",
            "HH:mm:ss.SSS"
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


            monitorParentProcessExit(serverParams, server::close);

            logger.info(
                "Reporting {} RMI registry socket address back to the client: {}",
                SonarLintServer.class.getSimpleName(),
                server.getSocketAddress()
            );
            serverRuntimeInfo.reportServerRegistrySocketAddress(server.getSocketAddress());

            logger.info("Join {}", SonarLintServer.class.getSimpleName());
            server.join();

            logger.warn("Exiting");
        }
    }

    private static void monitorParentProcessExit(SonarLintServerParams serverParams, Runnable onExit) {
        var clientPid = serverParams.getClientPid();
        var parentHandle = ProcessHandle.of(clientPid).orElse(null);
        if (parentHandle == null) {
            onExit.run();
            throw new IllegalStateException("Parent process already exited");
        }

        var clientStartInstant = serverParams.getClientStartInstant().orElse(null);
        var currentParentStartInstant = parentHandle.info().startInstant().orElse(null);
        if (!Objects.equals(currentParentStartInstant, clientStartInstant)) {
            onExit.run();
            throw new IllegalStateException("Parent process already exited");
        }

        var onExitFuture = parentHandle.onExit().thenRun(onExit);
        var onExitMonitor = new Thread(sneakyThrowsRunnable(onExitFuture::get));
        onExitMonitor.setName(SonarLintServerMain.class.getSimpleName() + "-parent-on-exit-monitor");
        onExitMonitor.setDaemon(true);
        onExitMonitor.start();
    }

}
