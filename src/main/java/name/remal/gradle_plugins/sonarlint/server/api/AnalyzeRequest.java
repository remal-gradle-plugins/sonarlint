package name.remal.gradle_plugins.sonarlint.server.api;

import static lombok.AccessLevel.PRIVATE;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.Value;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;

@Value
@Builder
@RequiredArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PRIVATE, force = true)
@SuppressWarnings({"java:S1948", "cast"})
public class AnalyzeRequest implements ApiRequest {

    @NonNull
    String moduleId;

    @NonNull
    File repositoryRoot;

    @Singular
    List<SourceFile> sourceFiles;

    @Singular
    Map<String, String> sonarProperties;

    @Singular
    Set<SonarLintLanguage> enabledLanguages;

    @Singular
    Set<String> enabledRules;

    @Singular
    Set<String> disabledRules;

    @Singular("ruleProperties")
    Map<String, Map<String, String>> rulesProperties;

}
