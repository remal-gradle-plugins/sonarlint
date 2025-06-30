package name.remal.gradle_plugins.sonarlint.server;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.ParameterizedType;
import name.remal.gradle_plugins.sonarlint.server.api.ApiRequest;
import name.remal.gradle_plugins.sonarlint.server.api.ApiResponse;

abstract class AbstractHandler<
    Request extends ApiRequest,
    Response extends ApiResponse
    >
    implements Handler<Request, Response> {

    private final Class<Request> requestClass;

    @SuppressWarnings("unchecked")
    protected AbstractHandler() {
        var type = TypeToken.of(getClass()).getSupertype(Handler.class).getType();
        if (type instanceof ParameterizedType) {
            var requestType = TypeToken.of(((ParameterizedType) type).getActualTypeArguments()[0]).getRawType();
            requestClass = (Class<Request>) requestType;
        } else {
            throw new IllegalStateException("Not ParameterizedType: " + type);
        }
    }

    @Override
    public boolean canHandle(Request request) {
        return requestClass.isInstance(request);
    }

}
