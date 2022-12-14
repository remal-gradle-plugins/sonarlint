package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.join;
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
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SonarLintPluginFunctionalTest {

    private final GradleProject project;

    @BeforeEach
    void beforeEach() {
        project.forBuildFile(build -> {
            build.applyPlugin("name.remal.sonarlint");
            build.applyPlugin("java");
            build.append("repositories { mavenCentral() }");
            build.appendBuildDirMavenRepositories();
            build.append("sonarLint.ignoreFailures = true");
        });

        project.withoutConfigurationCache();
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
    void emptyBuildPerformsSuccessfully() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");
        project.assertBuildSuccessfully();
    }

    @Test
    void java() {
        project.getBuildFile().registerDefaultTask("sonarlintMain");

        project.getBuildFile().append("sonarLint.rules.enable('java:S1171')");

        val sourceFileRelativePath = "src/main/java/pkg/TestMap.java";
        project.writeTextFile(sourceFileRelativePath, join("\n", new String[]{
            "package pkg;",
            "",
            "import java.util.LinkedHashMap;",
            "",
            "public class TestMap extends LinkedHashMap<String, String> {",
            "",
            "    {",
            "        put(\"a\", \"b\");",
            "    }",
            "",
            "}",
            }));
        project.assertBuildSuccessfully();

        val issues = parseSonarLintIssuesOf(sourceFileRelativePath);
        assertThat(issues)
            .extracting(Issue::getRule)
            .contains("java:S1171");
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

}
