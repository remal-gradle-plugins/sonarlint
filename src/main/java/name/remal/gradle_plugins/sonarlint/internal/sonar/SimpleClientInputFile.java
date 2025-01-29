package name.remal.gradle_plugins.sonarlint.internal.sonar;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.readString;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

@Value
@Builder
public class SimpleClientInputFile implements ClientInputFile {

    @NonNull
    Path path;

    @NonNull
    String relativePath;

    boolean generated;

    boolean test;

    @Nullable
    Charset charset;


    @Override
    @SuppressWarnings("unchecked")
    public <G> G getClientObject() {
        return (G) this;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return newInputStream(path);
    }

    @Override
    public String contents() throws IOException {
        return readString(path, charset != null ? charset : UTF_8);
    }

    @Override
    public String relativePath() {
        return getRelativePath();
    }

    @Override
    public URI uri() {
        return path.toUri();
    }

}
