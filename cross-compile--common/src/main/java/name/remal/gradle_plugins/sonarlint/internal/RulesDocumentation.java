package name.remal.gradle_plugins.sonarlint.internal;

import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_EXPLICITLY;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.val;
import name.remal.gradle_plugins.toolkit.ObjectUtils;

public class RulesDocumentation implements Documentation {

    private final SortedMap<String, RuleDoc> rules = new TreeMap<>();

    public void rule(String ruleKey, Consumer<RuleDoc> action) {
        val ruleDoc = new RuleDoc();
        ruleDoc.setName(ruleKey);
        action.accept(ruleDoc);
        rules.put(ruleKey, ruleDoc);
    }

    @Override
    @SuppressWarnings("java:S3776")
    public String renderToText() {
        if (rules.isEmpty()) {
            return "No SonarLint rules found";
        }

        val message = new StringBuilder();
        rules.forEach((ruleKey, ruleDoc) -> {
            if (isNotEmpty(message)) {
                message.append("\n\n");
            }
            message.append(ruleKey);

            Optional.ofNullable(ruleDoc.getName())
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(desc -> message.append(" - ").append(desc));

            if (ruleDoc.getStatus() == DISABLED_EXPLICITLY) {
                message.append("\n  Disabled explicitly");
            } else if (ruleDoc.getStatus() == ENABLED_EXPLICITLY) {
                message.append("\n  Enabled explicitly");
            } else if (ruleDoc.getStatus() == ENABLED_BY_DEFAULT) {
                message.append("\n  Enabled by default");
            } else if (ruleDoc.getStatus() == DISABLED_BY_DEFAULT) {
                message.append("\n  Disabled by default");
            }

            Optional.ofNullable(ruleDoc.getLanguage())
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(language -> message.append("\n  Language: ").append(language));

            if (isNotEmpty(ruleDoc.params)) {
                message.append("\n  Params:");
                ruleDoc.params.forEach((paramKey, paramDoc) -> {
                    message.append("\n    ").append(paramKey);
                    Optional.ofNullable(paramDoc.getDescription())
                        .filter(ObjectUtils::isNotEmpty)
                        .ifPresent(desc -> message.append(" - ").append(desc));

                    Optional.ofNullable(paramDoc.getType())
                        .ifPresent(type -> message.append("\n      Type: ").append(type));

                    Optional.ofNullable(paramDoc.getPossibleValues())
                        .filter(ObjectUtils::isNotEmpty)
                        .ifPresent(values -> {
                            val valuesString = values.stream()
                                .filter(Objects::nonNull)
                                .map(value -> {
                                    if (value.trim().isEmpty()) {
                                        return '"' + value + '"';
                                    } else {
                                        return value;
                                    }
                                })
                                .collect(joining(", "));
                            message.append("\n      Possible values: ").append(valuesString);
                        });

                    Optional.ofNullable(paramDoc.getDefaultValue())
                        .filter(ObjectUtils::isNotEmpty)
                        .ifPresent(value -> message.append("\n      Default value: ").append(value));
                });
            }
        });
        return message.toString();
    }


    public enum RuleStatus {
        DISABLED_EXPLICITLY,
        ENABLED_EXPLICITLY,
        ENABLED_BY_DEFAULT,
        DISABLED_BY_DEFAULT,
    }

    @Data
    @FieldDefaults(level = PRIVATE)
    public static class RuleDoc {

        @Nullable
        String name;

        @Nullable
        RuleStatus status;

        @Nullable
        String language;

        private final SortedMap<String, RuleParamDoc> params = new TreeMap<>();

        public void param(String paramKey, Consumer<RuleParamDoc> action) {
            val ruleParamDoc = new RuleParamDoc();
            action.accept(ruleParamDoc);
            params.put(paramKey, ruleParamDoc);
        }

    }

    @Data
    @FieldDefaults(level = PRIVATE)
    public static class RuleParamDoc {

        @Nullable
        String description;

        @Nullable
        String type;

        @Nullable
        String defaultValue;

        final Collection<String> possibleValues = new LinkedHashSet<>();

        public void setPossibleValues(@Nullable Collection<String> possibleValues) {
            this.possibleValues.clear();
            if (isNotEmpty(possibleValues)) {
                possibleValues.stream()
                    .filter(Objects::nonNull)
                    .forEach(this.possibleValues::add);
            }
        }

    }

}
