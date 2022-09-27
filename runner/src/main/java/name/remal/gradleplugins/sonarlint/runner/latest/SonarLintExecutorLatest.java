package name.remal.gradleplugins.sonarlint.runner.latest;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static java.nio.file.Files.createDirectories;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static name.remal.gradleplugins.sonarlint.runner.NodeJsInfo.collectNodeJsInfoFor;
import static name.remal.gradleplugins.sonarlint.runner.common.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradleplugins.sonarlint.runner.common.RulesDocumentation.RuleStatus.DISABLED_EXPLICITLY;
import static name.remal.gradleplugins.sonarlint.runner.common.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradleplugins.sonarlint.runner.common.RulesDocumentation.RuleStatus.ENABLED_EXPLICITLY;
import static name.remal.gradleplugins.toolkit.ProxyUtils.toDynamicInterface;
import static name.remal.gradleplugins.toolkit.issues.Issue.newIssue;
import static name.remal.gradleplugins.toolkit.issues.IssueSeverity.ERROR;
import static name.remal.gradleplugins.toolkit.issues.IssueSeverity.INFO;
import static name.remal.gradleplugins.toolkit.issues.IssueSeverity.WARNING;

import com.google.auto.service.AutoService;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradleplugins.sonarlint.runner.common.PropertiesDocumentation;
import name.remal.gradleplugins.sonarlint.runner.common.RulesDocumentation;
import name.remal.gradleplugins.sonarlint.runner.common.SonarLintExecutor;
import name.remal.gradleplugins.sonarlint.shared.RunnerParams;
import name.remal.gradleplugins.sonarlint.shared.SourceFile;
import name.remal.gradleplugins.toolkit.ObjectUtils;
import name.remal.gradleplugins.toolkit.issues.HtmlMessage;
import name.remal.gradleplugins.toolkit.issues.Issue;
import name.remal.gradleplugins.toolkit.issues.TextMessage;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;

@AutoService(SonarLintExecutor.class)
public class SonarLintExecutorLatest implements SonarLintExecutor {

    private RunnerParams params;
    private StandaloneGlobalConfiguration engineConfig;
    private StandaloneAnalysisConfiguration analysisConfig;

    @Override
    @SneakyThrows
    public void init(Object untypedParams) {
        this.params = toDynamicInterface(untypedParams, RunnerParams.class);

        val nodeJsInfo = collectNodeJsInfoFor(params);

        //noinspection ConstantConditions
        this.engineConfig = StandaloneGlobalConfiguration.builder()
            .addEnabledLanguages(Language.values())
            .addPlugins(params.getToolClasspath().stream()
                .distinct()
                .filter(path -> {
                    try {
                        PluginInfo.create(path);
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .toArray(Path[]::new)
            )
            .setSonarLintUserHome(createDirectories(params.getHomeDir()))
            .setWorkDir(createDirectories(params.getWorkDir()))
            .setLogOutput(new GradleLogOutput())
            .setNodeJs(
                nodeJsInfo.getNodeJsPath(),
                Optional.ofNullable(nodeJsInfo.getVersion())
                    .map(Version::create)
                    .orElse(null)
            )
            .build();

        this.analysisConfig = StandaloneAnalysisConfiguration.builder()
            .setBaseDir(params.getProjectDir())
            .addInputFiles(params.getFiles().stream()
                .map(GradleClientInputFile::new)
                .collect(toList())
            )
            .addIncludedRules(params.getEnabledRules().stream()
                .map(RuleKey::parse)
                .collect(toList())
            )
            .addExcludedRules(params.getDisabledRules().stream()
                .map(RuleKey::parse)
                .collect(toList())
            )
            .putAllExtraProperties(params.getSonarProperties())
            .addRuleParameters(params.getRulesProperties().entrySet().stream().collect(toMap(
                entry -> RuleKey.parse(entry.getKey()),
                Entry::getValue
            )))
            .build();
    }

    @Override
    @SuppressWarnings("java:S3776")
    public List<?> analyze() {
        val engine = new StandaloneSonarLintEngineImpl(engineConfig);
        try {
            List<Issue> issues = new ArrayList<>();
            IssueListener issueListener = sonarIssue -> {
                synchronized (issues) {
                    val issue = newIssue(builder -> {
                        val sourceFile = Optional.ofNullable(sonarIssue.getInputFile())
                            .map(ClientInputFile::getClientObject)
                            .filter(SourceFile.class::isInstance)
                            .map(SourceFile.class::cast)
                            .map(SourceFile::getAbsolutePath)
                            .map(File::new)
                            .orElse(null);
                        if (sourceFile == null) {
                            return;
                        } else {
                            builder.sourceFile(sourceFile);
                        }

                        val message = Optional.ofNullable(sonarIssue.getMessage())
                            .filter(ObjectUtils::isNotEmpty)
                            .map(TextMessage::textMessageOf)
                            .orElse(null);
                        if (message == null) {
                            return;
                        } else {
                            builder.message(message);
                        }

                        val ruleKey = sonarIssue.getRuleKey();
                        if (ruleKey == null) {
                            return;
                        } else {
                            builder.rule(ruleKey);
                        }

                        Optional.ofNullable(sonarIssue.getSeverity())
                            .map(Enum::name)
                            .map(String::toUpperCase)
                            .ifPresent(severity -> {
                                switch (severity) {
                                    case "BLOCKER":
                                    case "CRITICAL":
                                    case "MAJOR":
                                        builder.severity(ERROR);
                                        break;
                                    case "MINOR":
                                        builder.severity(WARNING);
                                        break;
                                    default:
                                        builder.severity(INFO);
                                }
                            });

                        Optional.ofNullable(sonarIssue.getType())
                            .map(Enum::name)
                            .map(UPPER_UNDERSCORE.converterTo(UPPER_CAMEL))
                            .ifPresent(builder::category);

                        builder.startLine(sonarIssue.getStartLine());
                        builder.startColumn(sonarIssue.getStartLineOffset());
                        builder.endLine(sonarIssue.getEndLine());
                        builder.endColumn(sonarIssue.getEndLineOffset());

                        engine.getRuleDetails(ruleKey)
                            .map(StandaloneRuleDetails::getHtmlDescription)
                            .map(HtmlMessage::htmlMessageOf)
                            .ifPresent(builder::description);
                    });
                    issues.add(issue);
                }
            };

            engine.analyze(analysisConfig, issueListener, engineConfig.getLogOutput(), new GradleProgressMonitor());

            return issues;

        } finally {
            engine.stop();
        }
    }

    @Override
    public Object collectRulesDocumentation() {
        val engine = new StandaloneSonarLintEngineImpl(engineConfig);
        try {
            val rulesDoc = new RulesDocumentation();
            engine.getAllRuleDetails().forEach(rule -> rulesDoc.rule(rule.getKey(), ruleDoc -> {
                ruleDoc.setName(rule.getName());

                if (params.getDisabledRules().contains(rule.getKey())) {
                    ruleDoc.setStatus(DISABLED_EXPLICITLY);
                } else if (params.getEnabledRules().contains(rule.getKey())) {
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

    @Override
    public Object collectPropertiesDocumentation() {
        val propertyDefinitionsExtractor = new PropertyDefinitionsExtractorContainer(engineConfig);
        propertyDefinitionsExtractor.execute();

        val propertiesDoc = new PropertiesDocumentation();
        propertyDefinitionsExtractor
            .getPropertyDefinitions()
            .forEach(propDef -> propertiesDoc.property(propDef.key(), propDoc -> {
                propDoc.setName(propDef.name());
                propDoc.setDescription(propDef.description());
                Optional.ofNullable(propDef.type())
                    .map(Enum::name)
                    .ifPresent(propDoc::setType);
                propDoc.setDefaultValue(propDef.defaultValue());
            }));
        return propertiesDoc;
    }

}
