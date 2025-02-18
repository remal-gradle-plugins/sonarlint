package name.remal.gradle_plugins.sonarlint;

import static com.google.common.collect.Maps.immutableEntry;
import static java.util.Map.Entry.comparingByKey;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.unwrapProviders;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.toolkit.ObjectUtils;

@NoArgsConstructor(access = PRIVATE)
abstract class CanonizationUtils {

    public static Collection<String> canonizeRules(@Nullable Collection<?> rules) {
        Collection<String> result = new LinkedHashSet<>();
        if (rules != null) {
            rules.stream()
                .filter(Objects::nonNull)
                .map(ObjectUtils::unwrapProviders)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(not(String::isEmpty))
                .sorted()
                .forEach(result::add);
        }
        return result;
    }

    public static Collection<String> canonizeLanguages(@Nullable Collection<?> languages) {
        Collection<String> result = new LinkedHashSet<>();
        if (languages != null) {
            languages.stream()
                .filter(Objects::nonNull)
                .map(ObjectUtils::unwrapProviders)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(not(String::isEmpty))
                .map(String::toLowerCase)
                .sorted()
                .forEach(result::add);
        }
        return result;
    }

    public static Map<String, String> canonizeProperties(
        @Nullable Map<?, ?> properties
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        if (properties != null) {
            properties.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> immutableEntry(String.valueOf(entry.getKey()), unwrapProviders(entry.getValue())))
                .filter(entry -> entry.getValue() != null)
                .sorted(comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), String.valueOf(entry.getValue())));
        }
        return result;
    }

    public static Map<String, Map<String, String>> canonizeRulesProperties(
        @Nullable Map<?, ? extends Map<?, ?>> properties
    ) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (properties != null) {
            properties.entrySet().stream()
                .map(entry -> immutableEntry(String.valueOf(entry.getKey()), canonizeProperties(entry.getValue())))
                .filter(entry -> isNotEmpty(entry.getValue()))
                .sorted(comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        }
        return result;
    }

}
