package name.remal.gradle_plugins.sonarlint;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.writeString;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.PathUtils.createParentDirectories;
import static name.remal.gradle_plugins.toolkit.StringUtils.substringBefore;
import static org.apache.commons.lang3.StringUtils.capitalize;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;

@NoArgsConstructor(access = PRIVATE)
public abstract class RuleExamples {

    private static final Map<String, String> RULE_EXAMPLES = ImmutableMap.<String, String>builder()
        .put("css:S4670", join("\n", new String[]{
            "field {}",
        }))
        .put("cloudformation:S6333", join("\n", new String[]{
            "AWSTemplateFormatVersion: 2010-09-09",
            "Resources:",
            "  ExampleMethod:",
            "    Type: AWS::ApiGateway::Method",
            "    Properties:",
            "      AuthorizationType: NONE",
            "      HttpMethod: GET",
        }))
        .put("docker:S6596", join("\n", new String[]{
            "FROM node:latest",
        }))
        .put("Web:S5725", join("\n", new String[]{
            "<script src=\"https://cdn.example.com/latest/script.js\"/>",
        }))
        .put("java:S1133", join("\n", new String[]{
            "package pkg;",
            "",
            "public class JavaS1133 {",
            "",
            "    @Deprecated",
            "    void method() {",
            "        System.exit(1);",
            "    }",
            "",
            "}",
        }))
        .put("java:S1171", join("\n", new String[]{
            "package pkg;",
            "",
            "import java.util.LinkedHashMap;",
            "",
            "public class JavaS1171 extends LinkedHashMap<String, String> {",
            "",
            "    {",
            "        put(\"a\", \"b\");",
            "    }",
            "",
            "}",
        }))
        .put("javascript:S930", join("\n", new String[]{
            "function sum(a, b) {",
            "    return a + b;",
            "}",
            "",
            "sum(1, 2, 3);",
        }))
        .put("kotlin:S899", join("\n", new String[]{
            "package pkg",
            "",
            "import java.io.File",
            "",
            "fun doSomething(file: File, lock: Lock) {",
            "    file.delete()",
            "}",
        }))
        .put("kubernetes:S6868", join("\n", new String[]{
            "apiVersion: rbac.authorization.k8s.io/v1",
            "kind: Role",
            "metadata:",
            "  namespace: default",
            "  name: example-role",
            "rules:",
            "- apiGroups: [\"\"]",
            "  resources: [\"pods\"]",
            "  verbs: [\"get\"]",
            "- apiGroups: [\"\"]",
            "  resources: [\"pods/exec\"]",
            "  verbs: [\"create\"]",
        }))
        .put("scala:S4663", join("\n", new String[]{
            "/*  */",
        }))
        .put("terraform:S6414", join("\n", new String[]{
            "resource \"google_project_iam_audit_config\" \"example\" {",
            "    project = data.google_project.project.id",
            "    service = \"allServices\"",
            "    audit_log_config {",
            "        log_type = \"ADMIN_READ\"",
            "        exempted_members = [",
            "            \"user:rogue.administrator@gmail.com\",",
            "        ]",
            "    }",
            "}",
        }))
        .put("typescript:S909", join("\n", new String[]{
            "for (i = 0; i < 10; i++) {",
            "    if (i == 5) {",
            "        continue;",
            "    }",
            "    alert(\"i = \" + i);",
            "}",
        }))
        .put("xml:S2321", join("\n", new String[]{
            "<parent><child/></parent>",
        }))
        .build();


    @Unmodifiable
    public static Set<String> getConfiguredSonarExampleRules() {
        return RULE_EXAMPLES.keySet();
    }

    public static class ConfiguredSonarExampleRulesProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(
            ParameterDeclarations parameters,
            ExtensionContext context
        ) {
            return getConfiguredSonarExampleRules().stream()
                .map(Arguments::of);
        }
    }


    @Unmodifiable
    public static Set<String> getConfiguredSonarExampleRulesWithDistinctLanguage() {
        var seenLang = new LinkedHashSet<String>();
        return getConfiguredSonarExampleRules().stream()
            .filter(rule -> seenLang.add(getSonarRuleLanguage(rule)))
            .collect(toImmutableSet());
    }

    public static class ConfiguredSonarExampleRulesWithDistinctLanguageProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(
            ParameterDeclarations parameters,
            ExtensionContext context
        ) {
            return getConfiguredSonarExampleRulesWithDistinctLanguage().stream()
                .map(Arguments::of);
        }
    }


    public static String writeSonarRuleExample(
        File projectDir,
        String rule
    ) {
        return writeSonarRuleExample(projectDir, rule, __ -> { });
    }

    @SneakyThrows
    public static String writeSonarRuleExample(
        File projectDir,
        String rule,
        Consumer<RuleExampleParams.RuleExampleParamsBuilder> configurer
    ) {
        var paramsBuilder = RuleExampleParams.builder();
        configurer.accept(paramsBuilder);
        var params = paramsBuilder.build();

        var lang = getSonarRuleLanguage(rule);

        String relativeFilePath;
        if (params.getRelativeFilePath() != null) {
            relativeFilePath = params.getRelativeFilePath();
        } else {
            relativeFilePath = capitalize(rule.replace(":", ""));
            if (params.getFileExtension() != null) {
                relativeFilePath += "." + params.getFileExtension();
            } else if (lang.equals("docker")) {
                relativeFilePath += "/Dockerfile";
            } else {
                relativeFilePath += "." + getSonarRuleDefaultFileExtension(rule);
            }
        }
        relativeFilePath = params.getSrcDir() + "/pkg/" + relativeFilePath;

        var ruleExampleSource = RULE_EXAMPLES.get(rule);
        if (ruleExampleSource == null) {
            throw new AssertionError("No rule example for `" + rule + "`");
        }

        var fullPath = projectDir.toPath().toAbsolutePath().resolve(relativeFilePath);
        createParentDirectories(fullPath);
        writeString(fullPath, ruleExampleSource, UTF_8);

        return relativeFilePath;
    }


    private static final Map<String, String> RULE_LANGUAGE_DEFAULT_EXTENSION = ImmutableMap.<String, String>builder()
        .put("azureresourcemanager", "bicep")
        .put("cloudformation", "yml")
        .put("css", "css")
        //.put("docker", "dockerfile")
        .put("html", "html")
        .put("java", "java")
        .put("javascript", "js")
        .put("kotlin", "kt")
        .put("kubernetes", "yml")
        .put("scala", "scala")
        //.put("secrets", "???")
        .put("terraform", "tf")
        .put("typescript", "ts")
        .put("xml", "xml")
        .build();

    public static String getSonarRuleDefaultFileExtension(String rule) {
        var lang = getSonarRuleLanguage(rule);
        var extension = RULE_LANGUAGE_DEFAULT_EXTENSION.get(lang);
        if (extension == null) {
            throw new AssertionError("No file extension is configured for the rule `" + rule + "`");
        }
        return extension;
    }

    public static String getSonarRuleLanguage(String rule) {
        if (rule.startsWith("Web:")) {
            return "html";
        }

        return substringBefore(rule, ":");
    }

    public static SonarLintLanguage getSonarLintRuleLanguage(String rule) {
        if (rule.startsWith("javascript:")) {
            return SonarLintLanguage.JS;

        } else if (rule.startsWith("typescript:")) {
            return SonarLintLanguage.TS;
        }

        var name = getSonarRuleLanguage(rule).toUpperCase();
        return SonarLintLanguage.valueOf(name);
    }

}
