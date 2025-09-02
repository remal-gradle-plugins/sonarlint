package name.remal.gradle_plugins.sonarlint.internal.server;

import static org.slf4j.event.Level.INFO;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;
import org.immutables.value.Value;
import org.slf4j.event.Level;

@Value.Immutable
@SuppressWarnings("immutables:subtype")
public interface SonarLintServerParams extends SonarLintParams {

    InetAddress getLoopbackAddress();

    long getClientPid();

    Optional<Instant> getClientStartInstant();

    InetSocketAddress getServerRuntimeInfoSocketAddress();

    @Value.Default
    default Level getDefaultLogLevel() {
        return INFO;
    }

}
