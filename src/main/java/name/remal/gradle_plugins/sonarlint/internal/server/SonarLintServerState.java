package name.remal.gradle_plugins.sonarlint.internal.server;

import java.net.InetSocketAddress;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

interface SonarLintServerState {

    enum Created implements SonarLintServerState {
        SERVER_CREATED
    }


    @Value
    @Builder
    class Started implements SonarLintServerState {

        @NonNull
        InetSocketAddress socketAddress;

    }


    enum Stopped implements SonarLintServerState {
        SERVER_STOPPED
    }

}
