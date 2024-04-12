package name.remal.gradle_plugins.sonarlint;

import static java.lang.Runtime.getRuntime;

import com.google.auto.service.AutoService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import javax.annotation.Nullable;
import lombok.SneakyThrows;
import lombok.val;
import org.gradle.api.provider.ProviderFactory;

@AutoService(NodeJsExecutableMethods.class)
class NodeJsExecutableMethods_7_4 implements NodeJsExecutableMethods {

    @Override
    @SneakyThrows
    @Nullable
    public byte[] executeNodeJsVersion(ProviderFactory providers, File file) {
        val command = new String[]{file.getAbsolutePath(), "-v"};
        val process = getRuntime().exec(command);
        if (process.waitFor() != 0) {
            return null;
        }

        try (val inputStream = process.getInputStream()) {
            return read(inputStream);
        }
    }


    @SneakyThrows
    private static byte[] read(InputStream inputStream) {
        val output = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }

        return output.toByteArray();
    }

}
