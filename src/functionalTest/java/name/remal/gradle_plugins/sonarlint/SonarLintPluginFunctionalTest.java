package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static name.remal.gradle_plugins.toolkit.StringUtils.escapeGroovy;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
            build.append("repositories { mavenCentral() }");
            build.appendBuildDirMavenRepositories();
            build.append("sonarLint.ignoreFailures = true");

            DISABLED_RULES.forEach(ruleId ->
                project.getBuildFile().append(format(
                    "sonarLint.rules.disable('%s')",
                    escapeGroovy(ruleId)
                ))
            );
        });
    }

    private List<Issue> parseSonarLintIssues() {
        val reportFile = project.getProjectDir().toPath()
            .resolve("build/reports/sonarlint/sonarlintMain/sonarlintMain.xml");
        return new CheckstyleXmlIssuesParser().parseIssuesFrom(reportFile);
    }

    @SneakyThrows
    private List<Issue> parseSonarLintIssuesOf(String relativePath) {
        val fileToMatch = new File(project.getProjectDir(), relativePath).getCanonicalFile();

        List<Issue> fileIssues = new ArrayList<>();
        for (val issue : parseSonarLintIssues()) {
            val sourceFile = issue.getSourceFile().getCanonicalFile();
            if (sourceFile.equals(fileToMatch)) {
                fileIssues.add(issue);
            }
        }
        return fileIssues;
    }


    @Test
    void sonarLintProperties() {
        project.getBuildFile().registerDefaultTask("sonarLintProperties");
        project.assertBuildSuccessfully();
    }

    @Test
    void sonarLintRules() {
        project.getBuildFile().registerDefaultTask("sonarLintRules");
        project.assertBuildSuccessfully();
    }


    @Test
    void emptyBuildPerformsSuccessfully() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");
        project.assertBuildSuccessfully();
    }

    @Test
    void java() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

        val sourceFileRelativePath = addJavaS1171RuleExample("src/main/java");

        project.assertBuildSuccessfully();

        val issues = parseSonarLintIssuesOf("src/main/java/" + sourceFileRelativePath);
        assertThat(issues)
            .extracting(Issue::getRule)
            .contains("java:S1171");
    }

    @Test
    void generatedFiles() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

        val javaS1171SourceRelativePath = addJavaS1171RuleExample("build/generated-java");
        val javaS1133SourceRelativePath = addJavaS1133RuleExample("src/main/java");

        project.getBuildFile().append(format(
            "sonarLint.ignoredPaths.add('%s')",
            escapeGroovy(javaS1171SourceRelativePath)
        ));

        project.assertBuildSuccessfully();

        assertThat(parseSonarLintIssuesOf("build/generated-java/" + javaS1171SourceRelativePath))
            .isEmpty();
        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1133SourceRelativePath))
            .extracting(Issue::getRule)
            .contains("java:S1133");
    }

    @Test
    void ignoredPaths() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

        val javaS1171SourceRelativePath = addJavaS1171RuleExample("src/main/java");
        val javaS1133SourceRelativePath = addJavaS1133RuleExample("src/main/java");

        project.getBuildFile().append(format(
            "sonarLint.ignoredPaths.add('%s')",
            escapeGroovy(javaS1171SourceRelativePath)
        ));

        project.assertBuildSuccessfully();

        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1171SourceRelativePath))
            .isEmpty();
        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1133SourceRelativePath))
            .extracting(Issue::getRule)
            .contains("java:S1133");
    }

    @Test
    void ruleIgnoredPaths() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

        val javaS1171SourceRelativePath = addJavaS1171RuleExample("src/main/java");
        val javaS1133SourceRelativePath = addJavaS1133RuleExample("src/main/java");

        project.getBuildFile().append(format(
            "sonarLint.rules.rule('java:S1171', { ignoredPaths.add('%s') })",
            escapeGroovy(javaS1171SourceRelativePath)
        ));

        project.assertBuildSuccessfully();

        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1171SourceRelativePath))
            .isEmpty();
        assertThat(parseSonarLintIssuesOf("src/main/java/" + javaS1133SourceRelativePath))
            .extracting(Issue::getRule)
            .contains("java:S1133");
    }

    @Nested
    class LanguagesInclusion {

        private String sourceFileRelativePath;

        @BeforeEach
        void beforeEach() {
            project.getBuildFile().registerDefaultTask("sonarlintMain");
            sourceFileRelativePath = addJavaS1171RuleExample("src/main/java");
        }


        @Test
        void includedLanguage() {
            project.getBuildFile().append("sonarLint.languages.include('java')");

            val buildLog = project.assertBuildSuccessfully().getOutput();
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
            project.getBuildFile().append("sonarLint.languages.include('kotlin')");

            val buildLog = project.assertBuildSuccessfully().getOutput();
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
            project.getBuildFile().append("sonarLint.languages.exclude('java')");

            val buildLog = project.assertBuildSuccessfully().getOutput();
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
            project.getBuildFile().append("sonarLint.languages.exclude('kotlin')");

            val buildLog = project.assertBuildSuccessfully().getOutput();
            assertThat(buildLog)
                .doesNotContainPattern("^Plugin .+ is excluded because language")
                .doesNotContainPattern("^Plugin .+ is excluded because none of languages");

            val issues = parseSonarLintIssuesOf("src/main/java/" + sourceFileRelativePath);
            assertThat(issues)
                .extracting(Issue::getRule)
                .contains("java:S1171");
        }

    }


    private String addJavaS1171RuleExample(String srcDir) {
        project.getBuildFile().append(format(
            "tasks.sonarlintMain.source('%s')",
            escapeGroovy(srcDir)
        ));

        project.getBuildFile().append("sonarLint.rules.enable('java:S1171')");

        val sourceFileRelativePath = "pkg/JavaS1171RuleExample.java";
        project.writeTextFile(srcDir + '/' + sourceFileRelativePath, join("\n", new String[]{
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

        return sourceFileRelativePath;
    }

    private String addJavaS1133RuleExample(String srcDir) {
        project.getBuildFile().append(format(
            "tasks.sonarlintMain.source('%s')",
            escapeGroovy(srcDir)
        ));

        project.getBuildFile().append("sonarLint.rules.enable('java:S1133')");

        val sourceFileRelativePath = "pkg/JavaS1133RuleExample.java";
        project.writeTextFile(srcDir + '/' + sourceFileRelativePath, join("\n", new String[]{
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

        return sourceFileRelativePath;
    }

}
