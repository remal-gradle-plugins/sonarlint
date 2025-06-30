package name.remal.gradle_plugins.sonarlint.server;

import java.util.function.Consumer;
import name.remal.gradle_plugins.sonarlint.server.api.ApiRequest;
import name.remal.gradle_plugins.sonarlint.server.api.ApiResponse;

interface Handler<
    Request extends ApiRequest,
    Response extends ApiResponse
    >
    extends Comparable<Handler<?, ?>> {

    boolean canHandle(Request request);

    Response handle(Request request, Consumer<String> logMessagesConsumer);


    default int getOrder() {
        return 0;
    }

    @Override
    default int compareTo(Handler<?, ?> other) {
        var result = Integer.compare(getOrder(), other.getOrder());
        if (result == 0) {
            result = getClass().getName().compareTo(other.getClass().getName());
        }
        return result;
    }

}
