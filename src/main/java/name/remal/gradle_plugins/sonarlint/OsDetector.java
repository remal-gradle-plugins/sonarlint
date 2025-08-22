package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PUBLIC;

import com.tisonkun.os.core.Detected;
import com.tisonkun.os.core.Detector;
import com.tisonkun.os.core.FileOperationProvider;
import com.tisonkun.os.core.OS;
import com.tisonkun.os.core.SystemPropertyOperationProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ProviderFactory;
import org.jspecify.annotations.Nullable;

@CustomLog
@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class OsDetector {

    private static volatile Detected detectedOs;

    private static volatile Boolean isAlpine;

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

    @SuppressWarnings("java:S1075")
    public boolean isAlpine() {
        if (isAlpine == null) {
            synchronized (OsDetector.class) {
                if (isAlpine == null) {
                    if (getDetectedOs().os == OS.linux) {
                        var alpineReleaseFilePath = "/etc/alpine-release";
                        var alpineReleaseFile = getLayout().getProjectDirectory().file(alpineReleaseFilePath);
                        isAlpine = getProviders().fileContents(alpineReleaseFile).getAsBytes().isPresent();
                    } else {
                        isAlpine = false;
                    }
                }
            }
        }

        return isAlpine;
    }

    private Detected detect() {
        var detector = new Detector(
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
            var regularFile = getLayout().getProjectDirectory().file(filePath);
            var bytes = getProviders().fileContents(regularFile).getAsBytes().get();
            return new ByteArrayInputStream(bytes);
        }
    }


    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ProjectLayout getLayout();

}
