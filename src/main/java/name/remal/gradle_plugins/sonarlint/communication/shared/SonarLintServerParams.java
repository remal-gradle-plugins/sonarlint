package name.remal.gradle_plugins.sonarlint.communication.shared;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@SuppressWarnings("immutables:subtype")
public interface SonarLintServerParams extends SonarLintParams {

    InetAddress getLoopbackAddress();

    long getClientPid();

    Optional<Instant> getClientStartInstant();

    InetSocketAddress getServerRuntimeInfoSocketAddress();

}
