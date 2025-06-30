package name.remal.gradle_plugins.sonarlint.server.api;

import static lombok.AccessLevel.PRIVATE;

import java.io.Serializable;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;

@Value
@Builder
@RequiredArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PRIVATE, force = true)
@SuppressWarnings("java:S1948")
public class RulesDocumentationResponse implements DocumentationResponse<RulesDocumentation>, Serializable {

    @NonNull
    RulesDocumentation documentation;

}
