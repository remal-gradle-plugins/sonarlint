package name.remal.gradle_plugins.sonarlint;

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.unwrapProviders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.jetbrains.annotations.Unmodifiable;

@Getter
public abstract class SonarLintRulesSettings {

    @Input
    @org.gradle.api.tasks.Optional
    public abstract SetProperty<String> getEnabled();

    public void enable(String... rules) {
        getEnabled().addAll(rules);
    }

    public void enable(Provider<?> rules) {
        getEnabled().addAll(getProviders().provider(() -> {
            var value = unwrapProviders(rules);
            if (value == null) {
                return List.of();
            }

            if (value instanceof Iterable) {
                return StreamSupport.stream(((Iterable<?>) value).spliterator(), false)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(toUnmodifiableList());
            } else {
                return List.of(value.toString());
            }
        }));
    }


    @Input
    @org.gradle.api.tasks.Optional
    public abstract SetProperty<String> getDisabled();

    public void disable(String... rules) {
        getDisabled().addAll(rules);
    }

    public void disable(Provider<?> rules) {
        getDisabled().addAll(getProviders().provider(() -> {
            var value = unwrapProviders(rules);
            if (value == null) {
                return List.of();
            }

            if (value instanceof Iterable) {
                return StreamSupport.stream(((Iterable<?>) value).spliterator(), false)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(toUnmodifiableList());
            } else {
                return List.of(value.toString());
            }
        }));
    }


    @Nested
    @org.gradle.api.tasks.Optional
    public abstract MapProperty<String, SonarLintRuleSettings> getRulesSettings();

    public void rule(String rule, Action<? super SonarLintRuleSettings> action) {
        var settings = getRulesSettings().getting(rule).getOrNull();
        if (settings == null) {
            settings = getObjects().newInstance(SonarLintRuleSettings.class);
            getRulesSettings().put(rule, settings);
        }
        action.execute(settings);
    }


    @Internal
    @Unmodifiable
    final Map<String, Map<String, String>> getProperties() {
        var allProperties = new LinkedHashMap<String, Map<String, String>>();
        getRulesSettings().get().forEach((rule, settings) -> {
            var properties = settings.getProperties().get();
            if (!properties.isEmpty()) {
                allProperties.put(rule, properties);
            }
        });
        return unmodifiableMap(allProperties);
    }


    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

}
