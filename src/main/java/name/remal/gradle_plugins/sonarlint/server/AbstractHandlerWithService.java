package name.remal.gradle_plugins.sonarlint.server;

import name.remal.gradle_plugins.sonarlint.server.api.ApiRequest;
import name.remal.gradle_plugins.sonarlint.server.api.ApiResponse;

abstract class AbstractHandlerWithService<
    Request extends ApiRequest,
    Response extends ApiResponse
    >
    extends AbstractHandler<Request, Response>
    implements HandlerWithService<Handler<Request, Response>> {


}
