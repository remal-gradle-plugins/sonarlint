package name.remal.gradle_plugins.sonarlint.internal.server;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RegistryFactory.createRegistryOnAvailablePort;

import java.net.InetSocketAddress;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintAnalyzer;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintHelp;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintLifecycle;
import name.remal.gradle_plugins.sonarlint.internal.utils.UsedThreads;
import name.remal.gradle_plugins.toolkit.CloseablesContainer;
import org.jspecify.annotations.Nullable;

@SuperBuilder
@RequiredArgsConstructor(access = PRIVATE)
public class SonarLintServer implements SonarLintLifecycleDelegate {

    private final SonarLintServerParams params;


    private final AtomicReference<@Nullable InetSocketAddress> socketAddress = new AtomicReference<>();
    private final CloseablesContainer closeables = new CloseablesContainer();
    private final Phaser stopPhaser = new Phaser(1);


    @SneakyThrows
    public synchronized void start() {
        if (socketAddress.get() == null) {
            stop();
        }

        var registry = closeables.registerCloseable(createRegistryOnAvailablePort(
            params.getLoopbackAddress()
        ));
        socketAddress.set(registry.getSocketAddress());

        registry.bind(SonarLintLifecycle.class, new SonarLintLifecycleDefault(this));


        var usedThreads = new UsedThreads();
        closeables.registerCloseable(() -> usedThreads.getUsedThreads().forEach(Thread::interrupt));


        var sonarLintParams = ImmutableSonarLintParams.builder()
            .from(params)
            .build();
        var shared = closeables.registerCloseable(new SonarLintSharedCode(sonarLintParams));

        SonarLintAnalyzer analyzer = new SonarLintAnalyzerDefault(shared);
        analyzer = usedThreads.withRegisterThreadEveryCall(SonarLintAnalyzer.class, analyzer);
        registry.bind(SonarLintAnalyzer.class, analyzer);

        SonarLintHelp help = new SonarLintHelpDefault(shared);
        help = usedThreads.withRegisterThreadEveryCall(SonarLintHelp.class, help);
        registry.bind(SonarLintHelp.class, help);
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
