package name.remal.gradle_plugins.sonarlint.internal.server.api;

import static name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintAnalyzeParamsUtils.generateNextAnalyzeJobId;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import org.immutables.value.Value;

@Value.Immutable
public interface SonarLintAnalyzeParams extends Serializable {

    @Value.Default
    default String getJobId() {
        return generateNextAnalyzeJobId();
    }

    File getRepositoryRoot();

    String getModuleId();

    List<SourceFile> getSourceFiles();

    @Value.Default
    default Set<SonarLintLanguage> getEnabledLanguages() {
        return Set.of(SonarLintLanguage.values());
    }

    @Value.Default
    default Map<String, String> getSonarProperties() {
        return Map.of();
    }

    @Value.Default
    default boolean isEnableRulesActivatedByDefault() {
        return true;
    }

    @Value.Default
    default Set<String> getEnabledRulesConfig() {
        return Set.of();
    }

    @Value.Default
    default Set<String> getDisabledRulesConfig() {
        return Set.of();
    }

    @Value.Default
    default Map<String, Map<String, String>> getRulesPropertiesConfig() {
        return Map.of();
    }

}
