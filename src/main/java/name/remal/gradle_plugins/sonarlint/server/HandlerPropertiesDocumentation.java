package name.remal.gradle_plugins.sonarlint.server;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

import com.google.auto.service.AutoService;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintService;
import name.remal.gradle_plugins.sonarlint.server.api.PropertiesDocumentationRequest;
import name.remal.gradle_plugins.sonarlint.server.api.PropertiesDocumentationResponse;

@AutoService(Handler.class)
@NoArgsConstructor(force = true)
@With
@AllArgsConstructor(access = PRIVATE)
@SuppressWarnings({"rawtypes", "RedundantSuppression"})
class HandlerPropertiesDocumentation
    extends AbstractHandler<PropertiesDocumentationRequest, PropertiesDocumentationResponse>
    implements HandlerWithService<Handler<PropertiesDocumentationRequest, PropertiesDocumentationResponse>> {

    @Nullable
    private final SonarLintService service;

    @Override
    public PropertiesDocumentationResponse handle(
        PropertiesDocumentationRequest request,
        Consumer<String> logMessagesConsumer
    ) {
        var documentation = requireNonNull(service).collectPropertiesDocumentation(logMessagesConsumer);
        return PropertiesDocumentationResponse.builder()
            .documentation(documentation)
            .build();
    }

}
