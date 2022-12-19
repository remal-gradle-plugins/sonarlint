package name.remal.gradleplugins.sonarlint.internal.latest;

import static java.util.Collections.emptySet;
import static name.remal.gradleplugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradleplugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_EXPLICITLY;
import static name.remal.gradleplugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradleplugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_EXPLICITLY;
import static name.remal.gradleplugins.sonarlint.internal.StandaloneGlobalConfigurationFactory.createEngineConfig;

import com.google.auto.service.AutoService;
import java.util.Optional;
import lombok.val;
import name.remal.gradleplugins.sonarlint.internal.RulesDocumentation;
import name.remal.gradleplugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradleplugins.sonarlint.internal.SonarLintRulesDocumentationCollector;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.commons.Language;

@AutoService(SonarLintRulesDocumentationCollector.class)
final class SonarLintRulesDocumentationCollectorLatest implements SonarLintRulesDocumentationCollector {

    @Override
    public RulesDocumentation collectRulesDocumentation(SonarLintExecutionParams params) {
        val engineConfig = createEngineConfig(params);

        val engine = new StandaloneSonarLintEngineImpl(engineConfig);
        try {
            val rulesDoc = new RulesDocumentation();
            engine.getAllRuleDetails().forEach(rule -> rulesDoc.rule(rule.getKey(), ruleDoc -> {
                ruleDoc.setName(rule.getName());

                if (params.getDisabledRules().getOrElse(emptySet()).contains(rule.getKey())) {
                    ruleDoc.setStatus(DISABLED_EXPLICITLY);
                } else if (params.getEnabledRules().getOrElse(emptySet()).contains(rule.getKey())) {
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
                    paramDoc.setPossibleValues(param.possibleValues());
                    paramDoc.setDefaultValue(param.defaultValue());
                }));
            }));
            return rulesDoc;

        } finally {
            engine.stop();
        }
    }

}
