package name.remal.gradle_plugins.sonarlint;

import static com.google.common.collect.Maps.immutableEntry;
import static java.util.Map.Entry.comparingByKey;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.unwrapProviders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.NoArgsConstructor;
import org.gradle.api.provider.Provider;

@NoArgsConstructor(access = PRIVATE)
abstract class CanonizationUtils {

    public static Map<String, String> canonizeProperties(
        @Nullable Map<?, ?> properties
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        if (properties != null) {
            properties.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> {
                    var value = unwrapProviders(entry.getValue());
                    return value != null ? immutableEntry(String.valueOf(entry.getKey()), value) : null;
                })
                .filter(Objects::nonNull)
                .sorted(comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), String.valueOf(entry.getValue())));
        }
        return result;
    }

    public static Map<String, String> canonizeProperties(
        Provider<? extends Map<?, ?>> properties
    ) {
        return canonizeProperties(properties.getOrNull());
    }


    public static Map<String, Map<String, String>> canonizeRulesProperties(
        @Nullable Map<?, ? extends SonarLintRuleSettings> allRuleSettings
    ) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (allRuleSettings != null) {
            allRuleSettings.entrySet().stream()
                .map(entry -> immutableEntry(
                    String.valueOf(entry.getKey()),
                    canonizeProperties(entry.getValue().getProperties().getOrNull())
                ))
                .filter(entry -> isNotEmpty(entry.getValue()))
                .sorted(comparingByKey())
                .forEach(entry -> {
                    var ruleProperties = result.computeIfAbsent(entry.getKey(), __ -> new LinkedHashMap<>());
                    ruleProperties.putAll(entry.getValue());
                });
        }
        return result;
    }

    public static Map<String, Map<String, String>> canonizeRulesProperties(
        Provider<? extends Map<?, ? extends SonarLintRuleSettings>> allRuleSettings
    ) {
        return canonizeRulesProperties(allRuleSettings.get());
    }

}
