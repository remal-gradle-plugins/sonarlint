package name.remal.gradle_plugins.sonarlint.internal.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import org.jspecify.annotations.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

@RequiredArgsConstructor
@SuppressWarnings({"deprecation", "RedundantSuppression"})
class SimpleClientInputFile implements ClientInputFile {

    private final SourceFile sourceFile;

    @Override
    public String getPath() {
        return sourceFile.getFile().getPath();
    }

    @Override
    public boolean isTest() {
        return sourceFile.isTest();
    }

    @Nullable
    @Override
    @SuppressWarnings("java:S2259")
    public Charset getCharset() {
        return Optional.ofNullable(sourceFile.getEncoding())
            .map(Charset::forName)
            .orElse(null);
    }

    @Override
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public <G> G getClientObject() {
        return (G) sourceFile;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return newInputStream(sourceFile.getFile().toPath());
    }

    @Override
    public String contents() throws IOException {
        return readString(sourceFile.getFile().toPath(), requireNonNullElse(getCharset(), UTF_8));
    }

    @Override
    public String relativePath() {
        return sourceFile.getRelativePath();
    }

    @Override
    public URI uri() {
        return sourceFile.getFile().toURI();
    }

}
