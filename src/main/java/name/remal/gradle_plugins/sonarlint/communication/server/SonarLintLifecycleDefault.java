package name.remal.gradle_plugins.sonarlint.communication.server;

import java.rmi.RemoteException;
import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintLifecycle;

@RequiredArgsConstructor
class SonarLintLifecycleDefault implements SonarLintLifecycle {

    private final SonarLintLifecycleDelegate delegate;

    @Override
    public void stop() throws RemoteException {
        delegate.stop();
    }

}
