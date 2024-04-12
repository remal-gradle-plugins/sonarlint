package name.remal.gradle_plugins.sonarlint;

import java.io.File;
import javax.annotation.Nullable;
import org.gradle.api.provider.ProviderFactory;

interface NodeJsExecutableMethods {

    @Nullable
    byte[] executeNodeJsVersion(ProviderFactory providers, File file);

}
