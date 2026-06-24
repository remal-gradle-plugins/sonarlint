package name.remal.gradle_plugins.sonarlint.internal.server;

import static java.lang.System.nanoTime;

import java.rmi.RemoteException;
import java.time.Duration;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintHeartbeat;

class SonarLintHeartbeatDefault implements SonarLintHeartbeat {

    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private volatile long lastPingNanos = nanoTime();

    @Override
    public void ping() throws RemoteException {
        lastPingNanos = nanoTime();
    }

    public boolean isTimedOut() {
        return nanoTime() - lastPingNanos > TIMEOUT.toNanos();
    }

}
