package name.remal.gradle_plugins.sonarlint.internal;

import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_EXPLICITLY;
import static name.remal.gradle_plugins.toolkit.NumbersAwareStringComparator.numbersAwareStringComparator;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.doNotInline;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.toolkit.HtmlToTextUtils;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import org.jspecify.annotations.Nullable;

public class RulesDocumentation implements Documentation {

    @VisibleForTesting
    public static final String NO_SONARLINT_RULES_FOUND_LOG_MESSAGE =
        doNotInline("No SonarLint rules found");


    @Getter
    private final SortedMap<String, RuleDoc> rules = new TreeMap<>(numbersAwareStringComparator());

    public void rule(String ruleKey, Consumer<RuleDoc> action) {
        var ruleDoc = new RuleDoc();
        ruleDoc.setName(ruleKey);
        action.accept(ruleDoc);
        rules.put(ruleKey, ruleDoc);
    }

    @Override
    @SuppressWarnings("java:S3776")
    public String renderToText() {
        if (rules.isEmpty()) {
            return NO_SONARLINT_RULES_FOUND_LOG_MESSAGE;
        }

        var message = new StringBuilder();
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
                        .map(HtmlToTextUtils::convertHtmlToText)
                        .map(text -> text.replace("\n\n", "\n"))
                        .ifPresent(desc -> message.append(" - ").append(desc));

                    Optional.ofNullable(paramDoc.getType())
                        .ifPresent(type -> message.append("\n      Type: ").append(type));

                    Optional.ofNullable(paramDoc.getCurrentValue())
                        .filter(ObjectUtils::isNotEmpty)
                        .ifPresent(value -> message.append("\n      Current value: ").append(value));

                    Optional.of(paramDoc.getPossibleValues())
                        .filter(ObjectUtils::isNotEmpty)
                        .ifPresent(values -> {
                            var valuesString = values.stream()
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
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor(access = PRIVATE)
    public static class RuleDoc implements Serializable {

        @Nullable
        private String name;

        @Nullable
        private RuleStatus status;

        @Nullable
        private String language;

        private final SortedMap<String, RuleParamDoc> params = new TreeMap<>();

        public void param(String paramKey, Consumer<RuleParamDoc> action) {
            var ruleParamDoc = new RuleParamDoc();
            action.accept(ruleParamDoc);
            params.put(paramKey, ruleParamDoc);
        }

    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor(access = PRIVATE)
    public static class RuleParamDoc implements Serializable {

        @Nullable
        private String description;

        @Nullable
        private String type;

        @Nullable
        private String currentValue;

        @Nullable
        private String defaultValue;

        private final Collection<String> possibleValues = new LinkedHashSet<>();

        public void setPossibleValues(@Nullable Collection<String> possibleValues) {
            this.possibleValues.clear();
            if (possibleValues != null) {
                possibleValues.stream()
                    .filter(Objects::nonNull)
                    .forEach(this.possibleValues::add);
            }
        }

    }

}
