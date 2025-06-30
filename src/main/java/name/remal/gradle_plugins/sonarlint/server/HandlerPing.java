package name.remal.gradle_plugins.sonarlint.server;

import static name.remal.gradle_plugins.sonarlint.server.api.PingResponse.PING_RESPONSE;

import com.google.auto.service.AutoService;
import java.util.function.Consumer;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.sonarlint.server.api.PingRequest;
import name.remal.gradle_plugins.sonarlint.server.api.PingResponse;

@AutoService(Handler.class)
@NoArgsConstructor(force = true)
@SuppressWarnings({"rawtypes", "RedundantSuppression"})
class HandlerPing
    extends AbstractHandler<PingRequest, PingResponse> {

    @Override
    public PingResponse handle(PingRequest request, Consumer<String> logMessagesConsumer) {
        return PING_RESPONSE;
    }

}
