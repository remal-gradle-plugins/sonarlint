package name.remal.gradle_plugins.sonarlint.communication.server.api;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import org.immutables.value.Value;

@Value.Immutable
public interface SonarLintAnalyzeParams extends Serializable {

    File getRepositoryRoot();

    String getModuleId();

    Collection<SourceFile> getSourceFiles();

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
