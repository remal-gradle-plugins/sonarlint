package name.remal.gradleplugins.sonarlint;

import static java.lang.String.join;
import static java.util.stream.Collectors.toList;
import static name.remal.gradleplugins.toolkit.PathUtils.normalizePath;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import name.remal.gradleplugins.toolkit.issues.CheckstyleXmlIssuesParser;
import name.remal.gradleplugins.toolkit.issues.Issue;
import name.remal.gradleplugins.toolkit.testkit.functional.GradleProject;
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
            build.registerDefaultTask("sonarlintMain");
        });
    }

    private List<Issue> parseSonarLintIssues() {
        val reportFile = project.getProjectDir().toPath()
            .resolve("build/reports/sonarlint/sonarlintMain/sonarlintMain.xml");
        return new CheckstyleXmlIssuesParser().parseIssuesFrom(reportFile);
    }

    private List<Issue> parseSonarLintIssues(String relativePath) {
        val projectPath = normalizePath(project.getProjectDir().toPath());
        val absolutePath = normalizePath(projectPath.resolve(relativePath));
        val absoluteFile = absolutePath.toFile();

        return parseSonarLintIssues().stream()
            .filter(issue -> absoluteFile.equals(issue.getSourceFile()))
            .collect(toList());
    }


    @Test
    void emptyBuildPerformsSuccessfully() {
        project.assertBuildSuccessfully();
    }

    @Test
    void java() {
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
        project.assertBuildFails();

        val issues = parseSonarLintIssues(sourceFileRelativePath);
        assertThat(issues)
            .extracting(Issue::getRule)
            .contains("java:S1171");
    }

}
