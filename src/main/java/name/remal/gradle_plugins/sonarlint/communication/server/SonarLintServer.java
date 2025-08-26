package name.remal.gradle_plugins.sonarlint.communication.server;

import static java.nio.file.Files.createTempDirectory;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.communication.utils.RegistryFactory.createRegistryOnAvailablePort;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;

import java.net.InetSocketAddress;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintAnalyze;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintHelp;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintLifecycle;
import name.remal.gradle_plugins.sonarlint.communication.shared.ImmutableSonarLintParams;
import name.remal.gradle_plugins.sonarlint.communication.shared.SonarLintServerParams;
import name.remal.gradle_plugins.toolkit.ClosablesContainer;
import org.jspecify.annotations.Nullable;

@SuperBuilder
@RequiredArgsConstructor(access = PRIVATE)
public class SonarLintServer implements SonarLintLifecycleDelegate {

    private final SonarLintServerParams params;


    private final AtomicReference<@Nullable InetSocketAddress> socketAddress = new AtomicReference<>();
    private final ClosablesContainer closeables = new ClosablesContainer();
    private final Phaser stopPhaser = new Phaser(1);


    @SneakyThrows
    public synchronized void start() {
        if (socketAddress.get() == null) {
            stop();
        }

        var tempDir = createTempDirectory(SonarLintServer.class.getName() + '-');
        closeables.registerCloseable(() -> {
            if (!tryToDeleteRecursively(tempDir)) {
                // ignore failures
            }
        });

        var registry = closeables.registerCloseable(createRegistryOnAvailablePort(
            params.getLoopbackAddress()
        ));
        socketAddress.set(registry.getSocketAddress());

        var sonarLintParams = ImmutableSonarLintParams.builder()
            .from(params)
            .build();

        registry.bind(SonarLintAnalyze.class, closeables.registerCloseable(
            SonarLintAnalyzeImpl.builder()
                .params(sonarLintParams)
                .tempDir(tempDir)
                .build()
        ));

        registry.bind(SonarLintHelp.class, closeables.registerCloseable(
            SonarLintHelpImpl.builder()
                .params(sonarLintParams)
                .tempDir(tempDir)
                .build()
        ));

        registry.bind(SonarLintLifecycle.class,
            SonarLintLifecycleImpl.builder()
                .delegate(this)
                .build()
        );
    }

    public synchronized InetSocketAddress getSocketAddress() {
        var socketAddress = this.socketAddress.get();
        if (socketAddress == null) {
            throw new IllegalStateException("Not started");
        }

        return socketAddress;
    }

    public void join() {
        if (socketAddress.get() == null) {
            throw new IllegalStateException("Not started");
        }

        stopPhaser.awaitAdvance(stopPhaser.getPhase());
    }

    @Override
    public synchronized void stop() {
        if (socketAddress.get() == null) {
            // not started, do nothing
            return;
        }

        try {
            socketAddress.set(null);
            closeables.close();

        } finally {
            stopPhaser.arrive();
        }
    }

}
