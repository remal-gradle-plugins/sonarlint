package name.remal.gradle_plugins.sonarlint;

import java.io.File;
import org.gradle.api.provider.ProviderFactory;

interface NodeJsExecutableMethods {

    NodeJsVersionResult getNodeJsVersion(ProviderFactory providers, File file);

}
