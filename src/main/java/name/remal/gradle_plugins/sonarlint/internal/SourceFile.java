package name.remal.gradle_plugins.sonarlint.internal;

import static lombok.AccessLevel.PRIVATE;

import java.io.File;
import java.io.Serializable;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
@RequiredArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PRIVATE, force = true)
public class SourceFile implements SourceFileInterface, Serializable {

    private static final long serialVersionUID = 1L;


    @NonNull
    File file;

    @NonNull
    String relativePath;

    boolean test;

    @Nullable
    String encoding;

}
