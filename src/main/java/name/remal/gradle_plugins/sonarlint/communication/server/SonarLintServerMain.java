package name.remal.gradle_plugins.sonarlint.communication.server;

import static java.nio.file.Files.readAllBytes;
import static name.remal.gradle_plugins.sonarlint.communication.utils.RegistryFactory.connectToRegistry;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.deserializeFrom;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsRunnable;

import java.nio.file.Paths;
import java.util.Objects;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.communication.client.api.SonarLintServerRuntimeInfo;

public class SonarLintServerMain {

    @SneakyThrows
    public static void main(String[] args) {
        var serverParamsFile = Paths.get(args[0]);
        var serverParams = deserializeFrom(readAllBytes(serverParamsFile), SonarLintServerParams.class);
        setupLogging(serverParams);
        startServer(serverParams);
    }

    private static void setupLogging(SonarLintServerParams serverParams) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", serverParams.getDefaultLogLevel().toString());
    }

    @SneakyThrows
    private static void startServer(SonarLintServerParams serverParams) {
        var serverRuntimeInfo = connectToRegistry(serverParams.getServerRuntimeInfoSocketAddress())
            .lookup(SonarLintServerRuntimeInfo.class);

        var server = SonarLintServer.builder()
            .params(serverParams)
            .build();
        try {
            server.start();
            monitorParentProcessExit(serverParams, server::stop);
            serverRuntimeInfo.reportServerRegistrySocketAddress(server.getSocketAddress());
            server.join();

        } finally {
            server.stop();
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
