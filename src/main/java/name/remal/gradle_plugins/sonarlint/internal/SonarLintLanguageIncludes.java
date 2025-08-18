package name.remal.gradle_plugins.sonarlint.internal;

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

    private static final Map<String, GlobPattern> PATTERNS_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("UnnecessaryLambda")
    private static final Predicate<String> ALWAYS_FALSE_RELATIVE_PATH_PREDICATE = __ -> false;


    @Unmodifiable
    public static Map<SonarLintLanguage, Predicate<String>> getLanguageRelativePathPredicates(
        Map<String, String> sonarProperties
    ) {
        var languageRelativePathPredicates = ImmutableMap.<SonarLintLanguage, Predicate<String>>builder();
        getLanguageIncludes(sonarProperties).forEach((language, includes) -> {
            if (includes.isEmpty()) {
                languageRelativePathPredicates.put(language, ALWAYS_FALSE_RELATIVE_PATH_PREDICATE);
                return;
            }

            Predicate<String> predicate = relativePath -> includes.stream()
                .map(SonarLintLanguageIncludes::getCompiledPattern)
                .anyMatch(pattern -> pattern.matches(relativePath));
            languageRelativePathPredicates.put(language, predicate);
        });
        return languageRelativePathPredicates.build();
    }

    private static GlobPattern getCompiledPattern(String include) {
        return PATTERNS_CACHE.computeIfAbsent(include, GlobPattern::compile);
    }


    @Unmodifiable
    @SuppressWarnings("java:S3776")
    public static Map<SonarLintLanguage, @Unmodifiable Set<String>> getLanguageIncludes(
        Map<String, String> sonarProperties
    ) {
        var languagePatternSets = ImmutableMap.<SonarLintLanguage, Set<String>>builder();

        for (var language : SonarLintLanguage.values()) {
            var includes = ImmutableSet.<String>builder();

            do {
                var filenamePatternsPropKey = language.getFilenamePatternsPropKey();
                if (filenamePatternsPropKey != null) {
                    var filenamePatterns = sonarProperties.get(filenamePatternsPropKey);
                    if (isNotEmpty(filenamePatterns)) {
                        Splitter.on(',').splitToStream(filenamePatterns)
                            .map(String::trim)
                            .filter(ObjectUtils::isNotEmpty)
                            .map(pattern -> pattern.startsWith("**/") ? pattern : "**/" + pattern)
                            .forEach(includes::add);
                        break;
                    }
                }

                var fileSuffixesPropKey = language.getFileSuffixesPropKey();
                if (fileSuffixesPropKey != null) {
                    var fileSuffixes = sonarProperties.get(fileSuffixesPropKey);
                    if (isNotEmpty(fileSuffixes)) {
                        Splitter.on(',').splitToStream(fileSuffixes)
                            .map(String::trim)
                            .filter(ObjectUtils::isNotEmpty)
                            .map(suffix -> "**/*" + suffix)
                            .forEach(includes::add);
                        break;
                    }
                }

                {
                    includes.addAll(language.getDefaultFilenamePatterns());
                }
            } while (false);

            languagePatternSets.put(language, includes.build());
        }

        return languagePatternSets.build();
    }

}
