package name.remal.gradle_plugins.sonarlint.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static name.remal.gradle_plugins.sonarlint.internal.ErrorLogging.logError;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.ImmutableSourceFile.SourceFileBuilder;
import org.immutables.value.Value;

@Value.Immutable
public interface SourceFile extends Serializable {

    static SourceFileBuilder newSourceFileBuilder() {
        return ImmutableSourceFile.builder();
    }


    String getAbsolutePath();

    String getRelativePath();

    @Value.Default
    default boolean isTest() {
        return false;
    }

    @Value.Default
    default boolean isGenerated() {
        return false;
    }

    @Value.Default
    default String getCharsetName() {
        return UTF_8.name();
    }

    @Value.Lazy
    default Charset getCharset() {
        val name = getCharsetName();
        if (isNotEmpty(name)) {
            try {
                return Charset.forName(name);
            } catch (UnsupportedCharsetException e) {
                logError(e.toString());
            }
        }
        return UTF_8;
    }

}
