package name.remal.gradle_plugins.sonarlint.server.api;

import static lombok.AccessLevel.PRIVATE;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.Value;
import name.remal.gradle_plugins.toolkit.issues.Issue;

@Value
@Builder
@RequiredArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PRIVATE, force = true)
@SuppressWarnings("java:S1948")
public class AnalyzeResponse implements ApiResponse, Serializable {

    @Singular
    List<Issue> issues;

}
