package name.remal.gradle_plugins.sonarlint.internal.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import name.remal.gradle_plugins.sonarlint.internal.utils.ClientRegistryFacade;
import org.jspecify.annotations.Nullable;

interface SonarLintClientState {

    enum Created implements SonarLintClientState {
        CLIENT_CREATED
    }


    @Value
    @Builder
    class Starting implements SonarLintClientState {

        AtomicReference<@Nullable JavaExecProcess> serverProcess = new AtomicReference<>();

        @EqualsAndHashCode.Exclude
        CountDownLatch startedSignal = new CountDownLatch(1);

    }


    @Value
    @Builder
    class Started implements SonarLintClientState {

        @NonNull
        ClientRegistryFacade serverRegistry;

        @NonNull
        JavaExecProcess serverProcess;

    }


    enum Stopped implements SonarLintClientState {
        CLIENT_STOPPED
    }

}
