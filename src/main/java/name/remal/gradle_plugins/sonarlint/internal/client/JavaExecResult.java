package name.remal.gradle_plugins.sonarlint.internal.client;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursivelyIgnoringFailure;

import java.nio.charset.Charset;
import java.nio.file.Path;
import lombok.SneakyThrows;

public interface JavaExecResult extends AutoCloseable {

    Process getProcess();

    default void stopProcess() {
        getProcess().destroy();
    }


    Path getOutputFile();

    @SneakyThrows
    default String readOutput() {
        var bytes = readAllBytes(getOutputFile());
        var charset = Charset.defaultCharset();
        if (charset.equals(US_ASCII)) {
            charset = UTF_8;
        }
        return new String(bytes, charset);
    }


    default void close() {
        try {
            stopProcess();
        } finally {
            tryToDeleteRecursivelyIgnoringFailure(getOutputFile());
        }
    }

}
