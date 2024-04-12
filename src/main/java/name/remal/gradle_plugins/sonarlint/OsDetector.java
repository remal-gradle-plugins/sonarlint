package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import com.tisonkun.os.core.Detected;
import com.tisonkun.os.core.Detector;
import lombok.CustomLog;
import lombok.NoArgsConstructor;

@CustomLog
@NoArgsConstructor(access = PRIVATE)
abstract class OsDetector {

    public static final Detected DETECTED_OS = new Detector(logger::debug).detect();

}
