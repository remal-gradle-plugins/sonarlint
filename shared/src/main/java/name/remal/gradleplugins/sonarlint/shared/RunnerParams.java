package name.remal.gradleplugins.sonarlint.shared;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedReader;
import static java.nio.file.Files.write;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static name.remal.gradleplugins.sonarlint.shared.GsonUtils.GSON;
import static name.remal.gradleplugins.toolkit.PathUtils.createParentDirectories;
import static name.remal.gradleplugins.toolkit.PathUtils.normalizePath;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradleplugins.sonarlint.shared.ImmutableRunnerParams.RunnerParamsBuilder;
import org.immutables.gson.Gson;
import org.immutables.value.Value;

@Value.Immutable
@Gson.TypeAdapters
public interface RunnerParams extends Serializable {

    static RunnerParamsBuilder newRunnerParamsBuilder() {
        return ImmutableRunnerParams.builder();
    }


    @Value.Default
    default boolean isIgnoreFailures() {
        return false;
    }

    RunnerCommand getCommand();

    int getSonarLintMajorVersion();

    Path getProjectDir();

    Path getHomeDir();

    Path getWorkDir();

    List<Path> getToolClasspath();

    @Value.Default
    default List<SourceFile> getFiles() {
        return emptyList();
    }

    @Value.Default
    default Set<String> getEnabledRules() {
        return emptySet();
    }

    @Value.Default
    default Set<String> getDisabledRules() {
        return emptySet();
    }

    @Value.Default
    default Map<String, String> getSonarProperties() {
        return emptyMap();
    }

    @Value.Default
    default Map<String, Map<String, String>> getRulesProperties() {
        return emptyMap();
    }

    @Nullable
    @Value.Default
    default Path getXmlReportLocation() {
        return null;
    }

    @Nullable
    @Value.Default
    default Path getHtmlReportLocation() {
        return null;
    }


    @SneakyThrows
    static RunnerParams readRunnerParamsFrom(Path path) {
        path = normalizePath(path);
        try (val reader = newBufferedReader(path, UTF_8)) {
            return GSON.fromJson(reader, RunnerParams.class);
        }
    }

    @SneakyThrows
    default void writeTo(Path path) {
        val jsonString = GSON.toJson(this);
        write(createParentDirectories(normalizePath(path)), jsonString.getBytes(UTF_8));
    }

}
