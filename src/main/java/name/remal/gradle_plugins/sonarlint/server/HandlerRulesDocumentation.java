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
import name.remal.gradle_plugins.sonarlint.server.api.RulesDocumentationRequest;
import name.remal.gradle_plugins.sonarlint.server.api.RulesDocumentationResponse;

@AutoService(Handler.class)
@NoArgsConstructor(force = true)
@With
@AllArgsConstructor(access = PRIVATE)
@SuppressWarnings({"rawtypes", "RedundantSuppression"})
class HandlerRulesDocumentation
    extends AbstractHandler<RulesDocumentationRequest, RulesDocumentationResponse>
    implements HandlerWithService<Handler<RulesDocumentationRequest, RulesDocumentationResponse>> {

    @Nullable
    private final SonarLintService service;

    @Override
    public RulesDocumentationResponse handle(RulesDocumentationRequest request, Consumer<String> logMessagesConsumer) {
        var documentation = requireNonNull(service).collectRulesDocumentation(logMessagesConsumer);
        return RulesDocumentationResponse.builder()
            .documentation(documentation)
            .build();
    }

}
