package name.remal.gradleplugins.sonarlint.runner.latest;

import static java.lang.Math.floor;
import static java.lang.Math.round;
import static java.lang.String.format;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isNotEmpty;

import javax.annotation.Nullable;
import lombok.CustomLog;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

@CustomLog
class GradleProgressMonitor implements ClientProgressMonitor {

    @Nullable
    private String message;
    private boolean indeterminate;

    @Override
    public void setMessage(@Nullable String msg) {
        this.message = msg;
    }

    @Override
    @SuppressWarnings("java:S2629")
    public void setFraction(float fraction) {
        long percent = round(floor(fraction * 1000.0));
        if (isNotEmpty(message)) {
            logger.info(format("%s: %3d%%", message, percent));
        }
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        if (this.indeterminate && !indeterminate && isNotEmpty(message)) {
            logger.info("{}: 100%", message);
            message = null;
        }
        this.indeterminate = indeterminate;
    }

}
