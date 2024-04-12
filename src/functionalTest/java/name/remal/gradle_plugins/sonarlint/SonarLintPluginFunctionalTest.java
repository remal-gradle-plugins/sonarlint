package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrows;
import static name.remal.gradle_plugins.toolkit.StringUtils.escapeGroovy;
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
            build.append("sonarLint.ignoreFailures = true");

            DISABLED_RULES.forEach(ruleId ->
                project.getBuildFile().append(format(
                    "sonarLint.rules.disable('%s')",
                    escapeGroovy(ruleId)
                ))
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
    void html() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

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

        project.assertBuildSuccessfully();

        val issues = parseSonarLintIssuesOf("src/main/resources/" + sourceFileRelativePath);
        assertThat(issues)
            .extracting(Issue::getRule)
            .contains("Web:S5254");
    }

    @Test
    void generatedFiles() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

        val javaS1171SourceRelativePath = addJavaS1171RuleExample("build/generated-java");
        val javaS1133SourceRelativePath = addJavaS1133RuleExample("src/main/java");

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
            escapeGroovy("**/*S1171*")
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
                project.getBuildFile().append(
                    "dependencies { testImplementation files('" + escapeGroovy(file.getPath()) + "') }"
                );
            });

        project.getBuildFile().append(join("\n", new String[]{
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

        project.getBuildFile().append("sonarLint.rules.enable('java:S5785')");

        project.getBuildFile().registerDefaultTask("sonarlintTest");

        project.assertBuildSuccessfully();

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

    @Test
    void doesNotUseNonReproducibleVersions() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

        project.getBuildFile().append(join("\n", new String[]{
            "configurations.configureEach {",
            "    resolutionStrategy {",
            "        failOnNonReproducibleResolution()",
            "    }",
            "}"
        }));

        addJavaS1171RuleExample("src/main/java");

        project.assertBuildSuccessfully();
    }

    @Nested
    class Logging {

        @Test
        void issueDescriptionIsDisplayedByDefault() {
            project.getBuildFile().registerDefaultTask("sonarlintMain");
            addJavaS1171RuleExample("src/main/java");

            val buildResult = project.assertBuildSuccessfully();
            val output = normalizeString(buildResult.getOutput());

            assertThat(output).contains("\n  Why is this an issue?\n");
        }

        @Test
        void issueDescriptionIsHidden() {
            project.getBuildFile().registerDefaultTask("sonarlintMain");
            addJavaS1171RuleExample("src/main/java");

            project.getBuildFile().append("sonarLint { logging { withDescription = false } }");

            val buildResult = project.assertBuildSuccessfully();
            val output = normalizeString(buildResult.getOutput());

            assertThat(output).doesNotContain("\n  Why is this an issue?\n");
        }

    }


    private String addRuleExample(String srcDir, String rule, String sourceFileRelativePath, String content) {
        project.getBuildFile().append(format(
            "tasks.sonarlintMain.source('%s')",
            escapeGroovy(srcDir)
        ));

        project.getBuildFile().append(format(
            "sonarLint.rules.enable('%s')",
            escapeGroovy(rule)
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
