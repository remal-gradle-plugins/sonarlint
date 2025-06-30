package name.remal.gradle_plugins.sonarlint.server;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.server.api.ShutdownResponse.SHUTDOWN_RESPONSE;

import com.google.auto.service.AutoService;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import name.remal.gradle_plugins.sonarlint.server.api.ShutdownRequest;
import name.remal.gradle_plugins.sonarlint.server.api.ShutdownResponse;

@AutoService(Handler.class)
@NoArgsConstructor(force = true)
@With
@AllArgsConstructor(access = PRIVATE)
@SuppressWarnings({"rawtypes", "RedundantSuppression"})
class HandlerShutdown
    extends AbstractHandler<ShutdownRequest, ShutdownResponse>
    implements HandlerWithShutdown<Handler<ShutdownRequest, ShutdownResponse>> {

    @Nullable
    private final ServerShutdown shutdown;

    @Override
    public ShutdownResponse handle(ShutdownRequest request, Consumer<String> logMessagesConsumer) {
        requireNonNull(shutdown).shutdown();
        return SHUTDOWN_RESPONSE;
    }

}
