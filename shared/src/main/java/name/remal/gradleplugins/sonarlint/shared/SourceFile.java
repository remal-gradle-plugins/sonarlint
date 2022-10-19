package name.remal.gradleplugins.sonarlint.shared;

import static java.nio.charset.StandardCharsets.UTF_8;
import static name.remal.gradleplugins.sonarlint.shared.ErrorLogging.logError;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isNotEmpty;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import lombok.val;
import name.remal.gradleplugins.sonarlint.shared.ImmutableSourceFile.SourceFileBuilder;
import org.immutables.gson.Gson;
import org.immutables.value.Value;

@Value.Immutable
@Gson.TypeAdapters
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
