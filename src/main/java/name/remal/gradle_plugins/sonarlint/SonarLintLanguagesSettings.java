package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import javax.inject.Inject;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

public abstract class SonarLintLanguagesSettings {

    @Internal
    public abstract SetProperty<SonarLintLanguage> getIncludes();

    public void include(SonarLintLanguage... includes) {
        getIncludes().addAll(includes);
    }

    public void include(String... includes) {
        getIncludes().addAll(parse(includes));
    }


    @Internal
    public abstract SetProperty<SonarLintLanguage> getExcludes();

    public void exclude(SonarLintLanguage... excludes) {
        getExcludes().addAll(excludes);
    }

    public void exclude(String... excludes) {
        getExcludes().addAll(parse(excludes));
    }


    @Internal
    public abstract Property<Boolean> getIncludeInfra();

    {
        getIncludeInfra().convention(false);
    }

    @Internal
    public abstract Property<Boolean> getIncludeFrontend();

    {
        getIncludeFrontend().convention(false);
    }

    private Collection<SonarLintLanguage> getAutomaticallyExcludedLanguages() {
        var infraIncluded = getIncludeInfra().get();
        var frontendIncluded = getIncludeFrontend().get();
        return stream(SonarLintLanguage.values())
            .filter(lang -> {
                if (!infraIncluded && lang.getType() == SonarLintLanguageType.INFRA) {
                    return true;
                }
                if (!frontendIncluded && lang.getType() == SonarLintLanguageType.FRONTEND) {
                    return true;
                }
                return false;
            })
            .collect(toUnmodifiableList());
    }


    @Input
    @org.gradle.api.tasks.Optional
    protected Collection<SonarLintLanguage> getLanguagesToProcess() {
        var includedLanguages = getIncludes().get();
        var excludedLanguages = new LinkedHashSet<>(getExcludes().get());
        getAutomaticallyExcludedLanguages().stream()
            .filter(not(includedLanguages::contains))
            .forEach(excludedLanguages::add);

        return stream(SonarLintLanguage.values())
            .filter(language -> {
                if (!includedLanguages.isEmpty() && !includedLanguages.contains(language)) {
                    return false;
                }
                if (!excludedLanguages.isEmpty() && excludedLanguages.contains(language)) {
                    return false;
                }
                return true;
            })
            .collect(toUnmodifiableList());
    }


    @SuppressWarnings("java:S1119")
    private static Collection<SonarLintLanguage> parse(String... languages) {
        var result = new ArrayList<SonarLintLanguage>();

        forEachLanguage:
        for (var language : languages) {
            for (var sonarLang : SonarLintLanguage.values()) {
                if (language.equalsIgnoreCase(sonarLang.getDisplayName())) {
                    result.add(sonarLang);
                    continue forEachLanguage;
                }
            }

            for (var sonarLang : SonarLintLanguage.values()) {
                if (language.equalsIgnoreCase(sonarLang.name())) {
                    result.add(sonarLang);
                    continue forEachLanguage;
                }
            }

            throw new IllegalArgumentException(format(
                "Unsupported SonarLint language: `%s`. Only these languages are supported: %s.",
                language,
                stream(SonarLintLanguage.values())
                    .map(SonarLintLanguage::getDisplayName)
                    .collect(joining(", "))
            ));
        }

        return result;
    }


    @Inject
    protected abstract ProviderFactory getProviders();

}
