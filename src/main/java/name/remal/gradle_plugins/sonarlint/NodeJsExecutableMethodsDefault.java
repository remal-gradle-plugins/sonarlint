package name.remal.gradle_plugins.sonarlint;

import com.google.auto.service.AutoService;
import java.io.File;
import javax.annotation.Nullable;
import org.gradle.api.provider.ProviderFactory;

@AutoService(NodeJsExecutableMethods.class)
class NodeJsExecutableMethodsDefault implements NodeJsExecutableMethods {

    @Override
    @Nullable
    public byte[] executeNodeJsVersion(ProviderFactory providers, File file) {
        return providers.exec(spec -> {
            spec.commandLine(file.getAbsolutePath(), "-v");
        }).getStandardOutput().getAsBytes().get();
    }

}
