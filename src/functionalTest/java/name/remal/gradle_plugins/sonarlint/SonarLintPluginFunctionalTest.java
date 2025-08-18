package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.join;
import static name.remal.gradle_plugins.sonarlint.FunctionalTestConstants.CURRENT_MINOR_GRADLE_VERSION;
import static name.remal.gradle_plugins.sonarlint.RuleExamples.getSonarRuleLanguage;
import static name.remal.gradle_plugins.sonarlint.RuleExamples.writeSonarRuleExample;
import static name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation.NO_SONARLINT_PROPERTIES_FOUND_LOG_MESSAGE;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.NO_SONARLINT_RULES_FOUND_LOG_MESSAGE;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrows;
import static name.remal.gradle_plugins.toolkit.StringUtils.normalizeString;
import static name.remal.gradle_plugins.toolkit.StringUtils.substringBeforeLast;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.issues.CheckstyleXmlIssuesParser;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import name.remal.gradle_plugins.toolkit.testkit.MinTestableGradleVersion;
import name.remal.gradle_plugins.toolkit.testkit.functional.GradleProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@SuppressWarnings({"java:S5976", "java:S1854", "UnusedReturnValue"})
class SonarLintPluginFunctionalTest {

    final GradleProject project;

    @BeforeEach
    void beforeEach() {
        project.forBuildFile(build -> {
            build.applyPlugin("name.remal.sonarlint");
            build.applyPlugin("java");
            build.addBuildDirMavenRepositories();
            build.line("repositories { mavenCentral() }");
            build.block("sonarLint", sonarLint -> {
                sonarLint.line("ignoreFailures = true");
                sonarLint.line("rules.enable('no-rules:enabled-by-default')");
            });
        });

        project.addForbiddenMessage("Provided Node.js executable file does not exist.");
        project.addForbiddenMessage("Couldn't find the Node.js binary.");
        project.addForbiddenMessage("Failed to determine the version of Node.js");
        project.addForbiddenMessage("Unsupported Node.JS version detected");
        project.addForbiddenMessage("Embedded Node.js failed to deploy.");

        project.addForbiddenMessage("is excluded because language");
        project.addForbiddenMessage("is excluded because none of languages");
    }


    @Nested
    class HelpTasks {

        @Test
        void sonarLintProperties() {
            var buildResult = project.assertBuildSuccessfully("sonarLintProperties");

            assertThat(buildResult.getOutput())
                .doesNotContain(NO_SONARLINT_PROPERTIES_FOUND_LOG_MESSAGE);
        }

        @Test
        void sonarLintRules() {
            var buildResult = project.assertBuildSuccessfully("sonarLintRules");

            assertThat(buildResult.getOutput())
                .doesNotContain(NO_SONARLINT_RULES_FOUND_LOG_MESSAGE);
        }

    }


    @Test
    void emptyBuildPerformsSuccessfully() {
        project.assertBuildSuccessfully("sonarlintMain");
    }


    @Nested
    class RulePerLanguage {

        @Test
        void java() {
            new Assertions()
                .rule("java:S1133")
                .assertAllRulesAreRaised();
        }

        @Nested
        @MinTestableGradleVersion(CURRENT_MINOR_GRADLE_VERSION)
        class OtherLanguages {

            @Test
            void javascript() {
                new Assertions()
                    .rule("javascript:S930")
                    .assertAllRulesAreRaised();
            }

            @Test
            void terraform() {
                new Assertions()
                    .rule("terraform:S6414")
                    .assertAllRulesAreRaised();
            }

            @Test
            void xml() {
                new Assertions()
                    .rule("xml:S2321")
                    .assertAllRulesAreRaised();
            }

        }

    }


    @Nested
    class Settings {

        @Test
        void generatedFilesAreExcluded() {
            new Assertions()
                .rule("java:S1171", params -> params
                    .srcDir("build/generated-java")
                )
                .rule("java:S1133")
                .assertRulesAreNotRaised("java:S1171")
                .assertRulesAreRaised("java:S1133");
        }

        @Test
        void ignoredPathsAreSupported() {
            project.getBuildFile().line(
                "sonarLint.ignoredPaths.add('%s')",
                "**/*S1171*"
            );

            new Assertions()
                .rule("java:S1171")
                .rule("java:S1133")
                .assertRulesAreNotRaised("java:S1171")
                .assertRulesAreRaised("java:S1133");
        }

        @Test
        void ruleIgnoredPathsAreSupported() {
            project.forBuildFile(build -> build.line(
                "sonarLint.rules.rule('java:S1171', { ignoredPaths.add('%s') })",
                "**/*S1171*"
            ));

            new Assertions()
                .rule("java:S1171")
                .rule("java:S1133")
                .assertRulesAreNotRaised("java:S1171")
                .assertRulesAreRaised("java:S1133");
        }


        @Nested
        class Logging {

            @Test
            void issueDescriptionIsDisplayedByDefault() {
                new Assertions()
                    .rule("java:S1171")
                    .assertAllRulesAreRaised()
                    .assertBuildOutput(output ->
                        assertThat(output)
                            .contains("\n  Why is this an issue?\n")
                    );
            }

            @Test
            void issueDescriptionIsHidden() {
                project.getBuildFile().line("sonarLint { logging { withDescription = false } }");

                new Assertions()
                    .rule("java:S1171")
                    .assertAllRulesAreRaised()
                    .assertBuildOutput(output ->
                        assertThat(output)
                            .doesNotContain("\n  Why is this an issue?\n")
                    );
            }

        }


        @Nested
        class LanguagesInclusion {

            @Test
            void includedLanguage() {
                project.getBuildFile().line("sonarLint.languages.include('java')");

                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("java:S1171")
                    .assertAllRulesAreRaised();
            }

            @Test
            void includedOtherLanguage() {
                project.getBuildFile().line("sonarLint.languages.include('kotlin')");

                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("java:S1171")
                    .assertNoRulesAreRaised();
            }

            @Test
            @SuppressWarnings("java:S5841")
            void excludedLanguage() {
                project.getBuildFile().line("sonarLint.languages.exclude('java')");

                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("java:S1171")
                    .assertNoRulesAreRaised()
                    .assertBuildOutput(output ->
                        assertThat(output)
                            .doesNotContainPattern("^Plugin .+ is excluded because language")
                            .doesNotContainPattern("^Plugin .+ is excluded because none of languages")
                    );
            }

            @Test
            void excludedOtherLanguage() {
                project.getBuildFile().line("sonarLint.languages.exclude('kotlin')");

                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("java:S1171")
                    .assertAllRulesAreRaised()
                    .assertBuildOutput(output ->
                        assertThat(output)
                            .doesNotContainPattern("^Plugin .+ is excluded because language")
                            .doesNotContainPattern("^Plugin .+ is excluded because none of languages")
                    );
            }


            @Test
            void infraLanguagesAreExcludedByDefault() {
                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("cloudformation:S6333")
                    .assertNoRulesAreRaised();
            }

            @Test
            void infraLanguagesCanBeIncluded() {
                project.getBuildFile().line("sonarLint.languages.includeInfra = true");

                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("cloudformation:S6333")
                    .assertAllRulesAreRaised();
            }


            @Test
            void frontendLanguagesAreExcludedByDefault() {
                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("css:S4670")
                    .assertNoRulesAreRaised();
            }

            @Test
            void frontendLanguagesCanBeIncluded() {
                project.getBuildFile().line("sonarLint.languages.includeFrontend = true");

                new Assertions()
                    .includeLanguageByDefault(false)
                    .rule("css:S4670")
                    .assertAllRulesAreRaised();
            }

        }

    }

    @Test
    void javaLibrariesCorrectlyDefinedForTestSourceSet() {
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
            "build/reports/sonarLint/sonarlintTest/sonarlintTest.xml",
            "src/test/java/pkg/JavaDependencyTest.java"
        ))
            .extracting(Issue::getRule)
            .contains("java:S5785");
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

        writeRuleExample("java:S1171");

        project.assertBuildSuccessfully("sonarlintMain");
    }

    @Test
    void javaWithGStringCompilerArgs() {
        project.getBuildFile().line(join("\n", new String[]{
            "def paramValue = 'value'",
            "tasks.withType(JavaCompile).configureEach {",
            "  options.compilerArgs.add(\"-Aname=$paramValue\")",
            "}",
        }));

        project.getBuildFile().line("sonarLint.languages.include('java')");

        new Assertions()
            .rule("java:S1133")
            .assertAllRulesAreRaised();
    }


    @SuppressWarnings("UnusedReturnValue")
    class Assertions {

        private final LazyValue<BuildResult> buildResult = lazyValue(() ->
            project.assertBuildSuccessfully("sonarlintMain")
        );

        private final Set<String> writtenRuleExamples = new LinkedHashSet<>();

        @CheckReturnValue
        public Assertions rule(String rule) {
            return rule(rule, __ -> { });
        }

        @CheckReturnValue
        public Assertions rule(String rule, Consumer<RuleExampleParams.RuleExampleParamsBuilder> configurer) {
            if (buildResult.isInitialized()) {
                throw new IllegalStateException("Project has been built");
            }

            writeRuleExample(rule, configurer);
            writtenRuleExamples.add(rule);
            return this;
        }


        private boolean includeLanguageByDefault = true;

        @CheckReturnValue
        public Assertions includeLanguageByDefault(boolean includeLanguageByDefault) {
            this.includeLanguageByDefault = includeLanguageByDefault;
            return this;
        }


        @CanIgnoreReturnValue
        public Assertions assertBuildOutput(Consumer<String> outputVerifier) {
            var output = normalizeString(buildResult.get().getOutput());
            outputVerifier.accept(output);
            return this;
        }

        @CanIgnoreReturnValue
        public Assertions assertAllRulesAreRaised() {
            return assertRaisedIssues(issues ->
                assertThat(issues)
                    .extracting(Issue::getRule)
                    .containsAll(writtenRuleExamples)
            );
        }

        @CanIgnoreReturnValue
        public Assertions assertRulesAreRaised(String... rules) {
            return assertRaisedIssues(issues ->
                assertThat(issues)
                    .extracting(Issue::getRule)
                    .contains(rules)
            );
        }

        @CanIgnoreReturnValue
        public Assertions assertNoRulesAreRaised() {
            return assertRaisedIssues(issues ->
                assertThat(issues)
                    .isEmpty()
            );
        }

        @CanIgnoreReturnValue
        @SuppressWarnings("java:S5841")
        public Assertions assertRulesAreNotRaised(String... rules) {
            return assertRaisedIssues(issues ->
                assertThat(issues)
                    .extracting(Issue::getRule)
                    .doesNotContain(rules)
            );
        }

        @CanIgnoreReturnValue
        public Assertions assertRaisedIssues(Consumer<Collection<Issue>> issuesVerifier) {
            if (includeLanguageByDefault) {
                for (var rule : writtenRuleExamples) {
                    project.forBuildFile(build -> build.line(
                        "sonarLint.languages.include('%s')",
                        build.escapeString(getSonarRuleLanguage(rule))
                    ));
                }
            }

            buildResult.get();
            var issues = parseSonarLintIssues();
            issuesVerifier.accept(issues);
            return this;
        }

    }


    List<Issue> parseSonarLintIssues() {
        return parseSonarLintIssues(null);
    }

    List<Issue> parseSonarLintIssues(@Nullable String reportRelativePath) {
        if (reportRelativePath == null) {
            reportRelativePath = "build/reports/sonarLint/sonarlintMain/sonarlintMain.xml";
        }
        var reportFile = project.resolveRelativePath(reportRelativePath);
        return new CheckstyleXmlIssuesParser().parseIssuesFrom(reportFile);
    }

    List<Issue> parseSonarLintIssuesOf(String sourceFileRelativePath) {
        return parseSonarLintIssuesOf(null, sourceFileRelativePath);
    }

    @SneakyThrows
    List<Issue> parseSonarLintIssuesOf(@Nullable String reportRelativePath, String sourceFileRelativePath) {
        var issues = parseSonarLintIssues(reportRelativePath);
        var fileToMatch = new File(project.getProjectDir(), sourceFileRelativePath).getCanonicalFile();

        List<Issue> fileIssues = new ArrayList<>();
        for (var issue : issues) {
            var sourceFile = issue.getSourceFile().getCanonicalFile();
            if (sourceFile.equals(fileToMatch)) {
                fileIssues.add(issue);
            }
        }
        return fileIssues;
    }


    String writeRuleExample(String rule) {
        return writeRuleExample(rule, __ -> { });
    }

    String writeRuleExample(String rule, Consumer<RuleExampleParams.RuleExampleParamsBuilder> configurer) {
        var relativeFilePath = writeSonarRuleExample(project.getProjectDir(), rule, configurer);

        project.forBuildFile(build -> build.line(
            "tasks.sonarlintMain.source('%s')",
            substringBeforeLast(relativeFilePath, "/")
        ));

        project.forBuildFile(build -> build.line(
            "sonarLint.rules.enable('%s')",
            build.escapeString(rule)
        ));

        return relativeFilePath;
    }

}
