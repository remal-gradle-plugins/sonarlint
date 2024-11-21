package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static java.nio.file.Files.createDirectories;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static name.remal.gradle_plugins.sonarlint.internal.impl.GradleLogOutput.GRADLE_LOG_OUTPUT;
import static name.remal.gradle_plugins.sonarlint.internal.impl.GradleProgressMonitor.GRADLE_PROGRESS_MONITOR;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.extractRules;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.getEnabledLanguages;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.getNodeJsExecutable;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.getNodeJsVersion;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.getPluginJarLocations;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintUtils.loadPlugins;
import static name.remal.gradle_plugins.toolkit.PredicateUtils.not;
import static name.remal.gradle_plugins.toolkit.issues.Issue.newIssue;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.ERROR;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.INFO;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.WARNING;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.issues.HtmlMessage;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.issues.TextMessage;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class SonarLintAnalyzer {

    @SneakyThrows
    @SuppressWarnings({"java:S3776", "EnumOrdinal"})
    public Collection<Issue> analyze(SonarLintExecutionParams params) {
        val pluginJarLocations = getPluginJarLocations(params);
        val enabledLanguages = getEnabledLanguages(params);
        val nodeJsVersion = getNodeJsVersion(params);

        val filesToAnalyze = params.getSourceFiles().getOrElse(emptyList()).stream()
            .map(GradleClientInputFile::new)
            .collect(toList());

        val module = new ClientModuleInfo("module-key", new SimpleClientModuleFileSystem(filesToAnalyze));

        val analysisEngineConfiguration = AnalysisEngineConfiguration.builder()
            .setWorkDir(createDirectories(params.getWorkDir().get().getAsFile().toPath()))
            .setClientPid(0)
            .setExtraProperties(emptyMap())
            .setNodeJs(getNodeJsExecutable(params))
            .setModulesProvider(() -> singletonList(module))
            .build();

        Predicate<SourceFile> isIgnoredSourceFile = sourceFile -> {
            if (sourceFile.isGenerated()) {
                return params.getIsGeneratedCodeIgnored().getOrElse(true);
            }

            return false;
        };

        try (val loadedPlugins = loadPlugins(pluginJarLocations, enabledLanguages, nodeJsVersion)) {
            val rules = extractRules(loadedPlugins.getLoadedPlugins().getAllPluginInstancesByKeys(), enabledLanguages);

            val enabledRules = params.getEnabledRules().getOrElse(emptySet()).stream()
                .map(RuleKey::parse)
                .collect(toList());
            val disabledRules = params.getDisabledRules().getOrElse(emptySet()).stream()
                .map(RuleKey::parse)
                .collect(toList());
            val allRuleProperties = params.getRulesProperties().getOrElse(emptyMap()).entrySet().stream().collect(toMap(
                entry -> RuleKey.parse(entry.getKey()),
                Entry::getValue
            ));
            val activeRules = new ArrayList<ActiveRule>();
            rules.forEach((ruleKey, rule) -> {
                if (disabledRules.contains(ruleKey)) {
                    return;
                }
                if (!rule.activatedByDefault() && !enabledRules.contains(ruleKey)) {
                    return;
                }
                val language = SonarLanguage.forKey(rule.repository().language()).orElse(null);
                if (language == null) {
                    return;
                }
                val activeRule = new ActiveRule(ruleKey.toString(), language.getSonarLanguageKey());
                activeRule.setParams(allRuleProperties.getOrDefault(ruleKey, emptyMap()));
                activeRules.add(activeRule);
            });

            val analysisConfiguration = AnalysisConfiguration.builder()
                .setBaseDir(params.getProjectDir().get().getAsFile().toPath())
                .addInputFiles(filesToAnalyze)
                .putAllExtraProperties(params.getSonarProperties().getOrElse(emptyMap()))
                .addActiveRules(activeRules)
                .build();

            Collection<Issue> issues = new LinkedHashSet<>();
            Consumer<org.sonarsource.sonarlint.core.analysis.api.Issue> issueListener = sonarIssue -> {
                synchronized (issues) {
                    val sourceFile = Optional.ofNullable(sonarIssue.getInputFile())
                        .map(ClientInputFile::getClientObject)
                        .filter(SourceFile.class::isInstance)
                        .map(SourceFile.class::cast)
                        .filter(not(isIgnoredSourceFile))
                        .map(SourceFile::getAbsolutePath)
                        .map(File::new)
                        .orElse(null);
                    if (sourceFile == null) {
                        return;
                    }

                    val message = Optional.ofNullable(sonarIssue.getMessage())
                        .filter(ObjectUtils::isNotEmpty)
                        .map(TextMessage::textMessageOf)
                        .orElse(null);
                    if (message == null) {
                        return;
                    }

                    val issue = newIssue(builder -> {
                        builder.rule(sonarIssue.getRuleKey());
                        builder.message(message);

                        builder.sourceFile(sourceFile);
                        builder.startLine(sonarIssue.getStartLine());
                        builder.startColumn(sonarIssue.getStartLineOffset());
                        builder.endLine(sonarIssue.getEndLine());
                        builder.endColumn(sonarIssue.getEndLineOffset());


                        val rule = Optional.ofNullable(sonarIssue.getRuleKey())
                            .map(RuleKey::parse)
                            .map(rules::get)
                            .orElse(null);

                        Map<Enum<?>, Enum<?>> impacts = new LinkedHashMap<>();
                        if (sonarIssue.getOverriddenImpacts() != null) {
                            impacts.putAll(sonarIssue.getOverriddenImpacts());
                        }
                        if (impacts.isEmpty() && rule != null) {
                            impacts.putAll(rule.defaultImpacts());
                        }
                        Enum<?> impactSeverity = null;
                        Enum<?> softwareQuality = null;
                        for (val entry : impacts.entrySet()) {
                            if (impactSeverity == null
                                || impactSeverity.ordinal() < entry.getValue().ordinal()
                            ) {
                                impactSeverity = entry.getValue();
                                softwareQuality = entry.getKey();
                            }
                        }

                        Optional.ofNullable(impactSeverity)
                            .map(Enum::name)
                            .map(String::toUpperCase)
                            .ifPresent(severity -> {
                                switch (severity) {
                                    case "BLOCKER":
                                    case "CRITICAL":
                                    case "MAJOR":
                                    case "HIGH":
                                        builder.severity(ERROR);
                                        break;
                                    case "MINOR":
                                    case "MEDIUM":
                                        builder.severity(WARNING);
                                        break;
                                    default:
                                        builder.severity(INFO);
                                }
                            });

                        Optional.ofNullable(softwareQuality)
                            .map(Enum::name)
                            .map(UPPER_UNDERSCORE.converterTo(UPPER_CAMEL))
                            .ifPresent(builder::category);

                        Optional.ofNullable(rule)
                            .map(Rule::htmlDescription)
                            .map(HtmlMessage::htmlMessageOf)
                            .ifPresent(builder::description);
                    });
                    issues.add(issue);
                }
            };

            val engine = new AnalysisEngine(
                analysisEngineConfiguration,
                loadedPlugins.getLoadedPlugins(),
                GRADLE_LOG_OUTPUT
            );
            try {
                engine.post(
                    new AnalyzeCommand(
                        module.key(),
                        analysisConfiguration,
                        issueListener,
                        GRADLE_LOG_OUTPUT
                    ),
                    GRADLE_PROGRESS_MONITOR
                ).get(1, HOURS);

                return issues;

            } finally {
                engine.stop();
            }
        }
    }

}
