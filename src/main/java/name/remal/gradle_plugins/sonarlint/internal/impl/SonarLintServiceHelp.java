package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.lang.String.join;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static name.remal.gradle_plugins.sonarlint.SonarLintLanguage.KOTLIN;
import static name.remal.gradle_plugins.sonarlint.SonarLintLanguage.SCALA;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation.PropertyDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;
import org.jetbrains.annotations.VisibleForTesting;
import org.sonar.api.server.rule.RuleParamType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class SonarLintServiceHelp
    extends AbstractSonarLintService<SonarLintServiceHelpParams> {

    public SonarLintServiceHelp(SonarLintServiceHelpParams params) {
        super(params);
    }

    @VisibleForTesting
    PropertiesDocumentation collectPropertiesDocumentationWithoutEnrichment() {
        var propertiesDoc = new PropertiesDocumentation();
        allPropertyDefinitions.forEach(propDef -> propertiesDoc.property(propDef.key(), propDoc -> {
            propDoc.setName(propDef.name());
            propDoc.setCategory(Stream.of(propDef.category(), propDef.subCategory())
                .filter(Objects::nonNull)
                .filter(not(String::isEmpty))
                .collect(joining(" > "))
            );
            propDoc.setDescription(propDef.description());
            Optional.ofNullable(propDef.type())
                .map(Enum::name)
                .ifPresent(propDoc::setType);
            propDoc.setDefaultValue(propDef.defaultValue());
        }));
        return propertiesDoc;
    }

    public PropertiesDocumentation collectPropertiesDocumentation() {
        return withThreadLogger(() -> {
            var propertiesDoc = collectPropertiesDocumentationWithoutEnrichment();

            propertiesDoc.getProperties().computeIfAbsent("sonar.kotlin.file.suffixes", propertyKey -> {
                if (!loadedPlugins.get().getLoadedPlugins().getAllPluginInstancesByKeys().containsKey("kotlin")) {
                    return null;
                }

                return PropertyDocumentation.builder()
                    .name("File Suffixes")
                    .description("List of suffixes for files to analyze.")
                    .type("STRING")
                    .defaultValue(join(",", KOTLIN.getDefaultFileSuffixes()))
                    .build();
            });

            propertiesDoc.getProperties().computeIfAbsent("sonar.scala.file.suffixes", propertyKey -> {
                if (!loadedPlugins.get().getLoadedPlugins().getAllPluginInstancesByKeys().containsKey("sonarscala")) {
                    return null;
                }

                return PropertyDocumentation.builder()
                    .name("File Suffixes")
                    .description("List of suffixes for files to analyze.")
                    .type("STRING")
                    .defaultValue(join(",", SCALA.getDefaultFileSuffixes()))
                    .build();
            });

            return propertiesDoc;
        });
    }

    @SneakyThrows
    public RulesDocumentation collectRulesDocumentation() {
        return withThreadLogger(() -> {
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
        });
    }

}
