package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.join;
import static java.util.Arrays.asList;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrows;
import static name.remal.gradle_plugins.toolkit.StringUtils.normalizeString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesParser;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.testkit.functional.GradleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SonarLintPluginFunctionalTest {

    private static final List<String> DISABLED_RULES = asList(
        "java:S1118",
        "java:S1123",
        "java:S6355"
    );

    private final GradleProject project;

    @BeforeEach
    void beforeEach() {
        project.forBuildFile(build -> {
            build.applyPlugin("name.remal.sonarlint");
            build.applyPlugin("java");
            build.addMavenCentralRepository();
            build.addBuildDirMavenRepositories();
            build.line("sonarLint.ignoreFailures = true");

            DISABLED_RULES.forEach(ruleId ->
                project.getBuildFile().line(
                    "sonarLint.rules.disable('%s')",
                    build.escapeString(ruleId)
                )
            );
        });

        project.addForbiddenMessage("Provided Node.js executable file does not exist.");
        project.addForbiddenMessage("Couldn't find the Node.js binary.");
        project.addForbiddenMessage("Failed to determine the version of Node.js");
        project.addForbiddenMessage("Unsupported Node.JS version detected");
        project.addForbiddenMessage("Embedded Node.js failed to deploy.");
    }

    private List<Issue> parseSonarLintIssues(@Nullable String reportRelativePath) {
        if (reportRelativePath == null) {
            reportRelativePath = "build/reports/sonarlint/sonarlintMain/sonarlintMain.xml";
        }
        val reportFile = project.resolveRelativePath(reportRelativePath);
        return new CheckstyleXmlIssuesParser().parseIssuesFrom(reportFile);
    }

    private List<Issue> parseSonarLintIssuesOf(String sourceFileRelativePath) {
        return parseSonarLintIssuesOf(null, sourceFileRelativePath);
    }

    @SneakyThrows
    private List<Issue> parseSonarLintIssuesOf(@Nullable String reportRelativePath, String sourceFileRelativePath) {
        val issues = parseSonarLintIssues(reportRelativePath);
        val fileToMatch = new File(project.getProjectDir(), sourceFileRelativePath).getCanonicalFile();

        List<Issue> fileIssues = new ArrayList<>();
        for (val issue : issues) {
            val sourceFile = issue.getSourceFile().getCanonicalFile();
            if (sourceFile.equals(fileToMatch)) {
                fileIssues.add(issue);
            }
        }
        return fileIssues;
    }


    @Test
    void sonarLintProperties() {
        project.assertBuildSuccessfully("sonarLintProperties");
    }

    @Test
    void sonarLintRules() {
        project.assertBuildSuccessfully("sonarLintRules");
    }


    @Test
    void emptyBuildPerformsSuccessfully() {
        project.assertBuildSuccessfully("sonarlintMain");
    }

    @Test
    void java() {
        val sourceFileRelativePath = addJavaS1171RuleExample("src/main/java");

        project.assertBuildSuccessfully("sonarlintMain");

        val issues = parseSonarLintIssuesOf("src/main/java/" + sourceFileRelativePath);
        assertThat(issues)
            .extracting(Issue::getRule)
            .contains("java:S1171");
    }

    @Nested
    class Html {

        @Test
        void htmlWithDefaultConfiguration() {
            val sourceFileRelativePath = addRuleExample(
                "src/main/resources",
                "Web:S5254",
                "test.html",
                join("\n", new String[]{
                    "<!DOCTYPE html>",
                    "<html>",
                    "</html>",
                })
            );

            project.assertBuildSuccessfully("sonarlintMain");

            val issues = parseSonarLintIssuesOf("src/main/resources/" + sourceFileRelativePath);
            assertThat(issues)
                .isEmpty();
        }

        @Test
        void htmlWithNodeJsDetection() {
            project.getBuildFile().line("sonarLint { nodeJs { detectNodeJs = true } }");

            val sourceFileRelativePath = addRuleExample(
                "src/main/resources",
                "Web:S5254",
                "test.html",
                join("\n", new String[]{
                    "<!DOCTYPE html>",
                    "<html>",
                    "</html>",
                })
            );

            project.assertBuildSuccessfully("sonarlintMain");

            val issues = parseSonarLintIssuesOf("src/main/resources/" + sourceFileRelativePath);
            assertThat(issues)
                .extracting(Issue::getRule)
                .contains("Web:S5254");
        }

        @Test
        void htmlWithNodeJsDetectionAndCustomSuffix() {
            project.getBuildFile().line("sonarLint { nodeJs { detectNodeJs = true } }");
            project.getBuildFile().line("sonarLint { sonarProperty('sonar.html.file.suffixes', '.custom-html') }");

            val sourceFileRelativePath = addRuleExample(
                "src/main/resources",
                "Web:S5254",
                "test.custom-html",
                join("\n", new String[]{
                    "<!DOCTYPE html>",
                    "<html>",
                    "</html>",
                })
            );

            project.assertBuildSuccessfully("sonarlintMain");

            val issues = parseSonarLintIssuesOf("src/main/resources/" + sourceFileRelativePath);
            assertThat(issues)
                .extracting(Issue::getRule)
                .contains("Web:S5254");
        }

        @Test
        void htmlWithoutNodeJsDetection() {
            project.getBuildFile().line("sonarLint { nodeJs { detectNodeJs = false } }");

            val sourceFileRelativePath = addRuleExample(
                "src/main/resources",
                "Web:S5254",
                "test.html",
                join("\n", new String[]{
                    "<!DOCTYPE html>",
                    "<html>",
                    "</html>",
                })
            );

            project.assertBuildSuccessfully("sonarlintMain");

            val issues = parseSonarLintIssuesOf("src/main/resources/" + sourceFileRelativePath);
            assertThat(issues)
                .isEmpty();
        }

    }

    @Test
    void generatedFiles() {
        val javaS1171SourceRelativePath = addJavaS1171RuleExample("build/generated-java");
        val javaS1133SourceRelativePath = addJavaS1133RuleExample("src/main/java");

        project.assertBuildSuccessfully("sonarlintMain");

        assertThat(parseSonarLintIssuesOf("build/generated-java/" + javaS1171SourceRelativePath))
            .isEmpty();
        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1133SourceRelativePath))
            .extracting(Issue::getRule)
            .contains("java:S1133");
    }

    @Test
    void ignoredPaths() {
        val javaS1171SourceRelativePath = addJavaS1171RuleExample("src/main/java");
        val javaS1133SourceRelativePath = addJavaS1133RuleExample("src/main/java");

        project.getBuildFile().line(
            "sonarLint.ignoredPaths.add('%s')",
            "**/*S1171*"
        );

        project.assertBuildSuccessfully("sonarlintMain");

        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1171SourceRelativePath))
            .isEmpty();
        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1133SourceRelativePath))
            .extracting(Issue::getRule)
            .contains("java:S1133");
    }

    @Test
    void ruleIgnoredPaths() {
        val javaS1171SourceRelativePath = addJavaS1171RuleExample("src/main/java");
        val javaS1133SourceRelativePath = addJavaS1133RuleExample("src/main/java");

        project.forBuildFile(build -> build.line(
            "sonarLint.rules.rule('java:S1171', { ignoredPaths.add('%s') })",
            build.escapeString(javaS1171SourceRelativePath)
        ));

        project.assertBuildSuccessfully("sonarlintMain");

        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1171SourceRelativePath))
            .isEmpty();
        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1133SourceRelativePath))
            .extracting(Issue::getRule)
            .contains("java:S1133");
    }

    @Test
    void javaLibrariesCorrectedDefinedForTestSourceSet() {
        Stream.of(
                org.junit.jupiter.api.Assertions.class,
                org.junit.jupiter.api.Test.class
            )
            .map(Class::getProtectionDomain)
            .map(ProtectionDomain::getCodeSource)
            .map(CodeSource::getLocation)
            .distinct()
            .map(sneakyThrows(URL::toURI))
            .map(File::new)
            .map(File::getAbsoluteFile)
            .forEach(file -> {
                project.forBuildFile(build -> build.line(
                    "dependencies { testImplementation files('" + build.escapeString(file.getPath()) + "') }"
                ));
            });

        project.getBuildFile().line(join("\n", new String[]{
            "tasks.withType(JavaCompile).configureEach {",
            "    options.compilerArgs.add('-Xlint:none')",
            "}"
        }));

        project.writeTextFile("src/main/java/pkg/JavaDependency.java", join("\n", new String[]{
            "package pkg;",
            "",
            "public class JavaDependency {",
            "",
            "    public String hello() {",
            "        return \"hello\";",
            "    }",
            "",
            "}",
        }));

        project.writeTextFile("src/test/java/pkg/JavaDependencyTest.java", join("\n", new String[]{
            "package pkg;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertTrue;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class JavaDependencyTest {",
            "",
            "    JavaDependency dependency;",
            "",
            "    @Test",
            "    void testHello() {",
            "        assertTrue(\"hello\".equals(dependency.hello()));",
            "    }",
            "",
            "}",
        }));

        project.getBuildFile().line("sonarLint.rules.enable('java:S5785')");

        project.assertBuildSuccessfully("sonarlintTest");

        assertThat(parseSonarLintIssuesOf(
            "build/reports/sonarlint/sonarlintTest/sonarlintTest.xml",
            "src/test/java/pkg/JavaDependencyTest.java"
        ))
            .extracting(Issue::getRule)
            .contains("java:S5785");
    }

    @Nested
    class LanguagesInclusion {

        private String sourceFileRelativePath;

        @BeforeEach
        void beforeEach() {
            sourceFileRelativePath = addJavaS1171RuleExample("src/main/java");
        }


        @Test
        void includedLanguage() {
            project.getBuildFile().line("sonarLint.languages.include('java')");

            val buildLog = project.assertBuildSuccessfully("sonarlintMain").getOutput();
            assertThat(buildLog)
                .doesNotContainPattern("^Plugin .+ is excluded because language")
                .doesNotContainPattern("^Plugin .+ is excluded because none of languages");

            val issues = parseSonarLintIssuesOf("src/main/java/" + sourceFileRelativePath);
            assertThat(issues)
                .extracting(Issue::getRule)
                .contains("java:S1171");
        }

        @Test
        void includedOtherLanguage() {
            project.getBuildFile().line("sonarLint.languages.include('kotlin')");

            val buildLog = project.assertBuildSuccessfully("sonarlintMain").getOutput();
            assertThat(buildLog)
                .doesNotContainPattern("^Plugin .+ is excluded because language")
                .doesNotContainPattern("^Plugin .+ is excluded because none of languages");

            val issues = parseSonarLintIssuesOf("src/main/java/" + sourceFileRelativePath);
            assertThat(issues)
                .extracting(Issue::getRule)
                .doesNotContain("java:S1171");
        }

        @Test
        void excludedLanguage() {
            project.getBuildFile().line("sonarLint.languages.exclude('java')");

            val buildLog = project.assertBuildSuccessfully("sonarlintMain").getOutput();
            assertThat(buildLog)
                .doesNotContainPattern("^Plugin .+ is excluded because language")
                .doesNotContainPattern("^Plugin .+ is excluded because none of languages");

            val issues = parseSonarLintIssuesOf("src/main/java/" + sourceFileRelativePath);
            assertThat(issues)
                .extracting(Issue::getRule)
                .doesNotContain("java:S1171");
        }

        @Test
        void excludedOtherLanguage() {
            project.getBuildFile().line("sonarLint.languages.exclude('kotlin')");

            val buildLog = project.assertBuildSuccessfully("sonarlintMain").getOutput();
            assertThat(buildLog)
                .doesNotContainPattern("^Plugin .+ is excluded because language")
                .doesNotContainPattern("^Plugin .+ is excluded because none of languages");

            val issues = parseSonarLintIssuesOf("src/main/java/" + sourceFileRelativePath);
            assertThat(issues)
                .extracting(Issue::getRule)
                .contains("java:S1171");
        }

    }

    @Test
    void doesNotUseNonReproducibleVersions() {
        project.getBuildFile().line(join("\n", new String[]{
            "configurations.configureEach {",
            "    resolutionStrategy {",
            "        failOnNonReproducibleResolution()",
            "    }",
            "}"
        }));

        addJavaS1171RuleExample("src/main/java");

        project.assertBuildSuccessfully("sonarlintMain");
    }

    @Nested
    class Logging {

        @Test
        void issueDescriptionIsDisplayedByDefault() {
            addJavaS1171RuleExample("src/main/java");

            val buildResult = project.assertBuildSuccessfully("sonarlintMain");
            val output = normalizeString(buildResult.getOutput());

            assertThat(output).contains("\n  Why is this an issue?\n");
        }

        @Test
        void issueDescriptionIsHidden() {
            addJavaS1171RuleExample("src/main/java");

            project.getBuildFile().line("sonarLint { logging { withDescription = false } }");

            val buildResult = project.assertBuildSuccessfully("sonarlintMain");
            val output = normalizeString(buildResult.getOutput());

            assertThat(output).doesNotContain("\n  Why is this an issue?\n");
        }

    }


    private String addRuleExample(String srcDir, String rule, String sourceFileRelativePath, String content) {
        project.forBuildFile(build -> build.line(
            "tasks.sonarlintMain.source('%s')",
            build.escapeString(srcDir)
        ));

        project.forBuildFile(build -> build.line(
            "sonarLint.rules.enable('%s')",
            build.escapeString(rule)
        ));

        project.writeTextFile(srcDir + '/' + sourceFileRelativePath, content);

        return sourceFileRelativePath;
    }

    private String addJavaS1171RuleExample(String srcDir) {
        return addRuleExample(srcDir, "java:S1171", "pkg/JavaS1171RuleExample.java", join("\n", new String[]{
            "package pkg;",
            "",
            "import java.util.LinkedHashMap;",
            "",
            "public class JavaS1171RuleExample extends LinkedHashMap<String, String> {",
            "",
            "    {",
            "        put(\"a\", \"b\");",
            "    }",
            "",
            "}",
        }));
    }

    private String addJavaS1133RuleExample(String srcDir) {
        return addRuleExample(srcDir, "java:S1133", "pkg/JavaS1133RuleExample.java", join("\n", new String[]{
            "package pkg;",
            "",
            "public class JavaS1133RuleExample {",
            "",
            "    @Deprecated",
            "    void method() {",
            "        System.exit(1);",
            "    }",
            "",
            "}",
        }));
    }

}
