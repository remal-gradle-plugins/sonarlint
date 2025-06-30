package name.remal.gradle_plugins.sonarlint;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Collections.synchronizedMap;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

abstract class SonarLintBuildData implements BuildService<BuildServiceParameters.None> {

    private final Map<String, SonarLintLanguagesSettings> projectsLanguagesSettings =
        synchronizedMap(new LinkedHashMap<>());

    public void registerProjectLanguagesSettings(Project project, SonarLintLanguagesSettings languagesSettings) {
        projectsLanguagesSettings.put(project.getPath(), languagesSettings);
    }

    public Set<SonarLintLanguage> getLanguagesToProcess() {
        return projectsLanguagesSettings.values().stream()
            .map(SonarLintLanguagesSettings::getLanguagesToProcess)
            .flatMap(Collection::stream)
            .sorted()
            .collect(toImmutableSet());
    }

}
