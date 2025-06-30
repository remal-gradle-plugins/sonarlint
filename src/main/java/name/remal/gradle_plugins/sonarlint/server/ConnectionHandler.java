package name.remal.gradle_plugins.sonarlint.server;

import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.server.api.ShutdownResponse.SHUTDOWN_RESPONSE;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyListProxy;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsConsumer;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintService;
import name.remal.gradle_plugins.sonarlint.server.api.ApiRequest;
import name.remal.gradle_plugins.sonarlint.server.api.ApiResponse;
import name.remal.gradle_plugins.sonarlint.server.api.GenericErrorResponse;

@RequiredArgsConstructor
class ConnectionHandler implements Runnable {

    @SuppressWarnings("unchecked")
    private static final List<Handler<ApiRequest, ApiResponse>> HANDLERS =
        asLazyListProxy(() ->
            ServiceLoader.load(Handler.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(it -> (Handler<ApiRequest, ApiResponse>) it)
                .sorted()
                .collect(toUnmodifiableList())
        );


    private final Socket socket;
    private final String expectedAuthToken;
    private final SonarLintService service;
    private final ServerShutdown shutdown;

    @Override
    @SneakyThrows
    public void run() {
        try (
            var inputStream = socket.getInputStream();
            var outputStream = socket.getOutputStream()
        ) {
            try (
                var in = new ObjectInputStream(inputStream);
                var out = new ObjectOutputStream(outputStream)
            ) {
                handle(in, out);
                out.flush();

            } finally {
                drain(inputStream);
                outputStream.flush();
            }

        } finally {
            socket.close();
        }
    }

    @SneakyThrows
    @SuppressWarnings("java:S2118")
    private void handle(ObjectInputStream in, ObjectOutputStream out) {
        var authToken = in.readUTF();
        if (!Objects.equals(expectedAuthToken, authToken)) {
            out.writeObject(GenericErrorResponse.builder()
                .message("Auth token mismatch")
                .build()
            );
            return;
        }

        var request = (ApiRequest) in.readObject();

        Consumer<String> logMessagesConsumer = sneakyThrowsConsumer(message -> {
            if (!message.isEmpty()) {
                out.writeUTF(message);
                out.flush();
            }
        });


        var response = handle(request, logMessagesConsumer);


        if (shutdown.isShutdown()) {
            response = SHUTDOWN_RESPONSE;
        }

        out.writeObject(response);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ApiResponse handle(ApiRequest request, Consumer<String> logMessagesConsumer) {
        for (var handler : HANDLERS) {
            if (handler.canHandle(request)) {
                if (handler instanceof HandlerWithService) {
                    handler = ((HandlerWithService<Handler>) handler)
                        .withService(service);
                }
                if (handler instanceof HandlerWithShutdown) {
                    handler = ((HandlerWithShutdown<Handler>) handler)
                        .withShutdown(shutdown);
                }

                return handler.handle(request, logMessagesConsumer);
            }
        }

        return GenericErrorResponse.builder()
            .message("Unsupported request: " + request)
            .build();
    }


    @SneakyThrows
    private static void drain(InputStream inputStream) {
        inputStream.transferTo(new OutputStream() {
            @Override
            public void write(int b) {
                // do nothing
            }
        });
    }

}
