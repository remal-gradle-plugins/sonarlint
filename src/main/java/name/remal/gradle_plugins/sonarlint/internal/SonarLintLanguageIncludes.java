package name.remal.gradle_plugins.sonarlint.internal;

import static com.google.common.base.Predicates.alwaysFalse;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.toolkit.GlobPattern;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import org.jetbrains.annotations.Unmodifiable;

@NoArgsConstructor(access = PRIVATE)
public abstract class SonarLintLanguageIncludes {

    @Unmodifiable
    public static Map<SonarLintLanguage, Predicate<String>> getAllLanguageRelativePathPredicates(
        Map<String, String> sonarProperties
    ) {
        var languageRelativePathPredicates = ImmutableMap.<SonarLintLanguage, Predicate<String>>builder();
        getAllLanguageIncludes(sonarProperties).forEach((language, includes) -> {
            var predicate = getLanguageRelativePathPredicate(language, sonarProperties);
            languageRelativePathPredicates.put(language, predicate);
        });
        return languageRelativePathPredicates.build();
    }

    public static Predicate<String> getLanguageRelativePathPredicate(
        SonarLintLanguage language,
        Map<String, String> sonarProperties
    ) {
        var includes = getLanguageIncludes(language, sonarProperties);
        if (includes.isEmpty()) {
            return alwaysFalse();
        }

        return relativePath ->
            includes.stream()
                .map(SonarLintLanguageIncludes::getCompiledPattern)
                .anyMatch(pattern -> pattern.matches(relativePath));
    }

    private static final Map<String, GlobPattern> PATTERNS_CACHE = new ConcurrentHashMap<>();

    private static GlobPattern getCompiledPattern(String include) {
        return PATTERNS_CACHE.computeIfAbsent(include, GlobPattern::compile);
    }


    @Unmodifiable
    public static Map<SonarLintLanguage, @Unmodifiable Set<String>> getAllLanguageIncludes(
        Map<String, String> sonarProperties
    ) {
        var languagePatternSets = ImmutableMap.<SonarLintLanguage, Set<String>>builder();
        for (var language : SonarLintLanguage.values()) {
            var includes = getLanguageIncludes(language, sonarProperties);
            languagePatternSets.put(language, includes);
        }
        return languagePatternSets.build();
    }

    @Unmodifiable
    public static Set<String> getLanguageIncludes(
        SonarLintLanguage language,
        Map<String, String> sonarProperties
    ) {
        var filenamePatternsPropKey = language.getFilenamePatternsPropKey();
        if (filenamePatternsPropKey != null) {
            var filenamePatterns = sonarProperties.get(filenamePatternsPropKey);
            if (isNotEmpty(filenamePatterns)) {
                return Splitter.on(',').splitToStream(filenamePatterns)
                    .map(String::trim)
                    .filter(ObjectUtils::isNotEmpty)
                    .map(pattern -> pattern.startsWith("**/") ? pattern : "**/" + pattern)
                    .collect(toImmutableSet());
            }
        }

        var fileSuffixesPropKey = language.getFileSuffixesPropKey();
        if (fileSuffixesPropKey != null) {
            var fileSuffixes = sonarProperties.get(fileSuffixesPropKey);
            if (isNotEmpty(fileSuffixes)) {
                return Splitter.on(',').splitToStream(fileSuffixes)
                    .map(String::trim)
                    .filter(ObjectUtils::isNotEmpty)
                    .map(suffix -> suffix.startsWith("**/*") ? suffix : "**/*" + suffix)
                    .collect(toImmutableSet());
            }
        }

        return ImmutableSet.copyOf(language.getDefaultFilenamePatterns());
    }

}
