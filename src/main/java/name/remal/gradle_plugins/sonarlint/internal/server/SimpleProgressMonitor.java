package name.remal.gradle_plugins.sonarlint.internal.server;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

@NoArgsConstructor(access = PRIVATE)
class SimpleProgressMonitor implements ProgressMonitor {

    public static final SimpleProgressMonitor SIMPLE_PROGRESS_MONITOR = new SimpleProgressMonitor();


    private static final Logger logger = LoggerFactory.getLogger(SimpleProgressMonitor.class);

    private final Thread executingThread = Thread.currentThread();

    @Override
    public boolean isCanceled() {
        return executingThread.isInterrupted();
    }


    @Override
    public void notifyProgress(@Nullable String message, @Nullable Integer percentage) {
        if (message != null && percentage != null) {
            logger.debug("{}: {}%", message, percentage);
        } else if (message != null) {
            logger.debug(message);
        }
    }


    @Override
    public void complete() {
        // do nothing
    }

    @Override
    public void cancel() {
        // do nothing
    }

}
