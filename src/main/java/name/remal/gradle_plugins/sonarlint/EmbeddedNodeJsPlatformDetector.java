package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.EmbeddedNodeJsPlatform.DARWIN_ARM64;
import static name.remal.gradle_plugins.sonarlint.EmbeddedNodeJsPlatform.DARWIN_X64;
import static name.remal.gradle_plugins.sonarlint.EmbeddedNodeJsPlatform.LINUX_ARM64;
import static name.remal.gradle_plugins.sonarlint.EmbeddedNodeJsPlatform.LINUX_X64;
import static name.remal.gradle_plugins.sonarlint.EmbeddedNodeJsPlatform.LINUX_X64_MUSL;
import static name.remal.gradle_plugins.sonarlint.EmbeddedNodeJsPlatform.WIN_X64;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;

import com.tisonkun.os.core.Arch;
import com.tisonkun.os.core.OS;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.toolkit.LazyValue;
import org.gradle.api.model.ObjectFactory;

@NoArgsConstructor(onConstructor_ = {@Inject})
abstract class EmbeddedNodeJsPlatformDetector {

    private final LazyValue<OsDetector> osDetector = lazyValue(() ->
        getObjects().newInstance(OsDetector.class)
    );

    @Nullable
    public EmbeddedNodeJsPlatform detect() {
        var osDetector = this.osDetector.get();
        var detectedOs = osDetector.getDetectedOs();
        if (detectedOs.os == OS.windows) {
            if (detectedOs.arch == Arch.x86_64) {
                return WIN_X64;
            }
        }

        if (detectedOs.os == OS.linux) {
            if (detectedOs.arch == Arch.aarch_64) {
                return LINUX_ARM64;
            } else if (detectedOs.arch == Arch.x86_64) {
                if (osDetector.isAlpine()) {
                    return LINUX_X64_MUSL;
                } else {
                    return LINUX_X64;
                }
            }
        }

        if (detectedOs.os == OS.osx) {
            if (detectedOs.arch == Arch.aarch_64) {
                return DARWIN_ARM64;
            } else if (detectedOs.arch == Arch.x86_64) {
                return DARWIN_X64;
            }
        }

        return null;
    }


    @Inject
    protected abstract ObjectFactory getObjects();

}
