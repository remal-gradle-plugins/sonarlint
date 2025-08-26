package name.remal.gradle_plugins.sonarlint.communication.server;

import static lombok.AccessLevel.PRIVATE;

import java.rmi.RemoteException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintLifecycle;

@Builder
@RequiredArgsConstructor(access = PRIVATE)
class SonarLintLifecycleImpl implements SonarLintLifecycle {

    private final SonarLintLifecycleDelegate delegate;

    @Override
    public void stop() throws RemoteException {
        delegate.stop();
    }

}
