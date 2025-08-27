package name.remal.gradle_plugins.sonarlint.communication.server;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static name.remal.gradle_plugins.toolkit.issues.Issue.newIssue;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.ERROR;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.INFO;
import static name.remal.gradle_plugins.toolkit.issues.IssueSeverity.WARNING;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.issues.HtmlMessage;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.issues.TextMessage;
import org.jspecify.annotations.Nullable;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

@RequiredArgsConstructor
class SonarIssueConverter {

    private final Map<RuleKey, RulesDefinition.Rule> allRules;

    @Nullable
    @SuppressWarnings({"java:S3776", "EnumOrdinal"})
    public Issue convert(org.sonarsource.sonarlint.core.analysis.api.Issue sonarIssue) {
        var sourceFile = getSourceFile(sonarIssue);
        if (sourceFile == null) {
            return null;
        }

        var message = Optional.ofNullable(sonarIssue.getMessage())
            .filter(ObjectUtils::isNotEmpty)
            .map(TextMessage::textMessageOf)
            .orElse(null);
        if (message == null) {
            return null;
        }

        var issue = newIssue(builder -> {
            builder.rule(sonarIssue.getRuleKey());
            builder.message(message);

            builder.sourceFile(sourceFile);
            builder.startLine(sonarIssue.getStartLine());
            builder.startColumn(sonarIssue.getStartLineOffset());
            builder.endLine(sonarIssue.getEndLine());
            builder.endColumn(sonarIssue.getEndLineOffset());


            var rule = Optional.ofNullable(sonarIssue.getRuleKey())
                .map(RuleKey::parse)
                .map(allRules::get)
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
            for (var entry : impacts.entrySet()) {
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

        return issue;
    }

    @Nullable
    @SuppressWarnings("java:S2637") // false positive
    private static File getSourceFile(org.sonarsource.sonarlint.core.analysis.api.Issue sonarIssue) {
        return Optional.ofNullable(sonarIssue.getInputFile())
            .map(ClientInputFile::getClientObject)
            .filter(SourceFile.class::isInstance)
            .map(SourceFile.class::cast)
            .map(SourceFile::getFile)
            .orElse(null);
    }

}
