package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.nio.file.Files.createTempDirectory;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyProxy;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

@SuppressWarnings("java:S2187")
class SonarLintServiceAnalysisComponentTest extends AbstractSonarLintServiceComponentTest {

    private static final Path tempPath = asLazyProxy(Path.class, () -> createTempDirectory("sonarlint-test-"));

    private static SonarLintServiceAnalysis service;

    @BeforeAll
    static void beforeAll() {
        var params = configureParamsBuilderBase(SonarLintServiceAnalysisParams.builder())
            .sonarUserHome(tempPath.resolve("sonar-user").toFile())
            .workDir(tempPath.resolve("sonar-work").toFile())
            .build();

        service = new SonarLintServiceAnalysis(params);
    }

    @AfterAll
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void afterAll() {
        service.close();
        tryToDeleteRecursively(tempPath);
    }

}
