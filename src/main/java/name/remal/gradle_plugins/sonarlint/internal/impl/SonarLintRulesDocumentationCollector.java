package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_EXPLICITLY;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.extractRules;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.getEnabledLanguages;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.getNodeJsVersion;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.getPluginJarLocations;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.loadPlugins;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class SonarLintRulesDocumentationCollector {

    @SneakyThrows
    public RulesDocumentation collectRulesDocumentation(SonarLintExecutionParams params) {
        var pluginJarLocations = getPluginJarLocations(params);
        var enabledLanguages = getEnabledLanguages(params);
        var nodeJsVersion = getNodeJsVersion(params);

        final Map<RuleKey, Rule> rules;
        try (var loadedPlugins = loadPlugins(pluginJarLocations, enabledLanguages, nodeJsVersion)) {
            rules = extractRules(loadedPlugins.getLoadedPlugins().getAllPluginInstancesByKeys(), enabledLanguages);
        }

        var enabledRules = params.getEnabledRules().getOrElse(emptySet()).stream()
            .map(RuleKey::parse)
            .collect(toList());
        var disabledRules = params.getDisabledRules().getOrElse(emptySet()).stream()
            .map(RuleKey::parse)
            .collect(toList());
        var allRuleProperties = params.getRulesProperties().getOrElse(emptyMap()).entrySet().stream().collect(toMap(
            entry -> RuleKey.parse(entry.getKey()),
            Entry::getValue
        ));

        var rulesDoc = new RulesDocumentation();
        rules.forEach((key, rule) -> rulesDoc.rule(key.toString(), ruleDoc -> {
            ruleDoc.setName(rule.name());

            if (disabledRules.contains(key)) {
                ruleDoc.setStatus(DISABLED_EXPLICITLY);
            } else if (enabledRules.contains(key)) {
                ruleDoc.setStatus(ENABLED_EXPLICITLY);
            } else if (rule.activatedByDefault()) {
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
                paramDoc.setCurrentValue(allRuleProperties.getOrDefault(key, emptyMap()).get(param.key()));
                paramDoc.setDefaultValue(param.defaultValue());
                paramDoc.setPossibleValues(param.type().values());
            }));
        }));
        return rulesDoc;
    }

}
