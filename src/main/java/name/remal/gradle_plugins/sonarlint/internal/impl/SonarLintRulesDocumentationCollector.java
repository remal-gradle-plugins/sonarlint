package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.createEngineConfig;

import java.util.Map.Entry;
import java.util.Optional;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.commons.Language;

public class SonarLintRulesDocumentationCollector {

    public RulesDocumentation collectRulesDocumentation(SonarLintExecutionParams params) {
        val enabledRules = params.getEnabledRules().get().stream()
            .map(RuleKey::parse)
            .collect(toList());
        val disabledRules = params.getDisabledRules().get().stream()
            .map(RuleKey::parse)
            .collect(toList());
        val allRuleProperties = params.getRulesProperties().get().entrySet().stream().collect(toMap(
            entry -> RuleKey.parse(entry.getKey()),
            Entry::getValue
        ));

        val engineConfig = createEngineConfig(params);

        val engine = new StandaloneSonarLintEngineImpl(engineConfig);
        try {
            val rulesDoc = new RulesDocumentation();
            engine.getAllRuleDetails().forEach(rule -> rulesDoc.rule(rule.getKey(), ruleDoc -> {
                ruleDoc.setName(rule.getName());

                val ruleKey = RuleKey.parse(rule.getKey());
                if (disabledRules.contains(ruleKey)) {
                    ruleDoc.setStatus(DISABLED_EXPLICITLY);
                } else if (enabledRules.contains(ruleKey)) {
                    ruleDoc.setStatus(ENABLED_EXPLICITLY);
                } else if (rule.isActiveByDefault()) {
                    ruleDoc.setStatus(ENABLED_BY_DEFAULT);
                } else {
                    ruleDoc.setStatus(DISABLED_BY_DEFAULT);
                }

                Optional.ofNullable(rule.getLanguage())
                    .map(Language::getLabel)
                    .ifPresent(ruleDoc::setLanguage);

                rule.paramDetails().forEach(param -> ruleDoc.param(param.key(), paramDoc -> {
                    paramDoc.setDescription(param.description());
                    Optional.ofNullable(param.type())
                        .map(Enum::name)
                        .ifPresent(paramDoc::setType);
                    paramDoc.setCurrentValue(allRuleProperties.getOrDefault(ruleKey, emptyMap()).get(param.key()));
                    paramDoc.setDefaultValue(param.defaultValue());
                    paramDoc.setPossibleValues(param.possibleValues());
                }));
            }));
            return rulesDoc;

        } finally {
            engine.stop();
        }
    }

}
