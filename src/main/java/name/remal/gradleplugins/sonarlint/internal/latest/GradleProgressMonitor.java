package name.remal.gradleplugins.sonarlint.internal.latest;

import static java.lang.Math.floor;
import static java.lang.Math.round;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import javax.annotation.Nullable;
import lombok.CustomLog;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

@CustomLog
class GradleProgressMonitor implements ClientProgressMonitor {

    @Nullable
    private String message;
    private int prevPercent = -1;
    private boolean indeterminate;

    @Override
    public void setMessage(@Nullable String msg) {
        this.message = msg;
    }


    private void logPercent(int percent) {
        if (isNotEmpty(message) && percent != prevPercent) {
            logger.info(format("%s: %d%%", message, percent));
            prevPercent = percent;
        }
    }

    @Override
    @SuppressWarnings("java:S2629")
    public void setFraction(float fraction) {
        int percent = toIntExact(round(floor(fraction * 100.0)));
        logPercent(percent);
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        if (this.indeterminate && !indeterminate && isNotEmpty(message)) {
            logPercent(100);
            message = null;
            prevPercent = -1;
        }
        this.indeterminate = indeterminate;
    }

}
