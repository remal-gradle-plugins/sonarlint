package name.remal.gradle_plugins.sonarlint;

import com.tisonkun.os.core.Detected;
import com.tisonkun.os.core.Detector;
import com.tisonkun.os.core.FileOperationProvider;
import com.tisonkun.os.core.SystemPropertyOperationProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ProviderFactory;

@CustomLog
@RequiredArgsConstructor(onConstructor_ = {@Inject})
abstract class OsDetector {

    private static volatile Detected detectedOs;

    public Detected getDetectedOs() {
        if (detectedOs == null) {
            synchronized (OsDetector.class) {
                if (detectedOs == null) {
                    detectedOs = detect();
                }
            }
        }

        return detectedOs;
    }

    private Detected detect() {
        val detector = new Detector(
            new SystemPropertyOperationProviderImpl(),
            new FileOperationProviderImpl(),
            logger::debug
        );
        return detector.detect();
    }

    private class SystemPropertyOperationProviderImpl implements SystemPropertyOperationProvider {
        @Override
        @Nullable
        public String getSystemProperty(String name) {
            return getProviders().systemProperty(name).getOrNull();
        }

        @Override
        public String getSystemProperty(String name, String defaultValue) {
            return getProviders().systemProperty(name).getOrElse(defaultValue);
        }

        @Override
        public String setSystemProperty(String name, String value) {
            // do nothing
            return value;
        }
    }

    private class FileOperationProviderImpl implements FileOperationProvider {
        @Override
        public InputStream readFile(String filePath) {
            val regularFile = getLayout().getProjectDirectory().file(filePath);
            val bytes = getProviders().fileContents(regularFile).getAsBytes().get();
            return new ByteArrayInputStream(bytes);
        }
    }


    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ProjectLayout getLayout();

}
