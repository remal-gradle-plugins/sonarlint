package name.remal.gradle_plugins.sonarlint;

import java.io.File;
import javax.annotation.Nullable;

interface NodeJsDetectorWithRootDir {

    void setRootDir(@Nullable File rootDir);

}
