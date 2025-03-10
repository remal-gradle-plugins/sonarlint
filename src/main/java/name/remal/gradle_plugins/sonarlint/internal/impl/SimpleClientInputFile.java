package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.io.ByteStreams.toByteArray;
import static java.nio.file.Files.newInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

@RequiredArgsConstructor
@SuppressWarnings({"deprecation", "RedundantSuppression"})
class SimpleClientInputFile implements ClientInputFile {

    private final SourceFile sourceFile;

    @Override
    public String getPath() {
        return sourceFile.getAbsolutePath();
    }

    @Override
    public boolean isTest() {
        return sourceFile.isTest();
    }

    @Nullable
    @Override
    public Charset getCharset() {
        return sourceFile.getCharset();
    }

    @Override
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public <G> G getClientObject() {
        return (G) sourceFile;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return newInputStream(Paths.get(sourceFile.getAbsolutePath()));
    }

    @Override
    public String contents() throws IOException {
        try (var inputStream = inputStream()) {
            var bytes = toByteArray(inputStream);
            return new String(bytes, sourceFile.getCharsetName());
        }
    }

    @Override
    public String relativePath() {
        return sourceFile.getRelativePath();
    }

    @Override
    public URI uri() {
        return Paths.get(sourceFile.getAbsolutePath()).toUri();
    }

}
