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
import name.remal.gradle_plugins.sonarlint.server.api.AnalyzeRequest;
import name.remal.gradle_plugins.sonarlint.server.api.AnalyzeResponse;

@AutoService(Handler.class)
@NoArgsConstructor(force = true)
@With
@AllArgsConstructor(access = PRIVATE)
@SuppressWarnings({"rawtypes", "RedundantSuppression"})
class HandlerAnalyze
    extends AbstractHandler<AnalyzeRequest, AnalyzeResponse>
    implements HandlerWithService<Handler<AnalyzeRequest, AnalyzeResponse>> {

    @Nullable
    private final SonarLintService service;

    @Override
    public AnalyzeResponse handle(AnalyzeRequest request, Consumer<String> logMessagesConsumer) {
        var issues = requireNonNull(service).analyze(
            request.getModuleId(),
            request.getRepositoryRoot(),
            request.getSourceFiles(),
            request.getSonarProperties(),
            request.getEnabledLanguages(),
            request.getEnabledRules(),
            request.getDisabledRules(),
            request.getRulesProperties(),
            logMessagesConsumer
        );
        return AnalyzeResponse.builder()
            .issues(issues)
            .build();
    }

}
