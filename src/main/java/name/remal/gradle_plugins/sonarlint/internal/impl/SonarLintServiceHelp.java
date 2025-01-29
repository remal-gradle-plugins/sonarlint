package name.remal.gradle_plugins.sonarlint.internal.impl;

import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;

import java.util.Optional;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;
import org.sonar.api.server.rule.RuleParamType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class SonarLintServiceHelp
    extends AbstractSonarLintService<SonarLintServiceHelpParams> {

    public SonarLintServiceHelp(SonarLintServiceHelpParams params) {
        super(params);
    }

    public PropertiesDocumentation collectPropertiesDocumentation() {
        var propertiesDoc = new PropertiesDocumentation();
        allPropertyDefinitions.forEach(propDef -> propertiesDoc.property(propDef.key(), propDoc -> {
            propDoc.setName(propDef.name());
            propDoc.setDescription(propDef.description());
            Optional.ofNullable(propDef.type())
                .map(Enum::name)
                .ifPresent(propDoc::setType);
            propDoc.setDefaultValue(propDef.defaultValue());
        }));
        return propertiesDoc;
    }

    @SneakyThrows
    public RulesDocumentation collectRulesDocumentation() {
        var rulesDoc = new RulesDocumentation();
        allRules.forEach((key, rule) -> rulesDoc.rule(key.toString(), ruleDoc -> {
            ruleDoc.setName(rule.name());

            if (rule.activatedByDefault()) {
                ruleDoc.setStatus(ENABLED_BY_DEFAULT);
            } else {
                ruleDoc.setStatus(DISABLED_BY_DEFAULT);
            }

            Optional.ofNullable(rule.repository().language())
                .flatMap(SonarLanguage::forKey)
                .map(SonarLanguage::getSonarLanguageKey)
                .ifPresent(ruleDoc::setLanguage);

            rule.params().forEach(param -> ruleDoc.param(param.key(), paramDoc -> {
                paramDoc.setDescription(param.description());
                Optional.ofNullable(param.type())
                    .map(RuleParamType::type)
                    .ifPresent(paramDoc::setType);
                paramDoc.setDefaultValue(param.defaultValue());
                paramDoc.setPossibleValues(param.type().values());
            }));
        }));
        return rulesDoc;
    }

}
