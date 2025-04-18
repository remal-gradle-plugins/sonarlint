package name.remal.gradle_plugins.sonarlint.internal;

import java.io.File;
import javax.annotation.Nullable;

public interface SourceFileInterface {

    File getFile();

    String getRelativePath();

    boolean isTest();

    @Nullable
    String getEncoding();

}
