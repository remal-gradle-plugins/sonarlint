package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.CustomLog;
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

@CustomLog
class SimpleProgressMonitor extends ProgressMonitor {

    public static final SimpleProgressMonitor SIMPLE_PROGRESS_MONITOR = new SimpleProgressMonitor();


    private SimpleProgressMonitor() {
        super(new SimpleSimpleProgressMonitor());
    }

    @Nullable
    @Override
    @SuppressWarnings("Slf4jFormatShouldBeConst")
    public <T> T startTask(String message, Supplier<T> task) {
        logger.info(message);
        return super.startTask(message, task);
    }


    private static class SimpleSimpleProgressMonitor implements ClientProgressMonitor {

        private final Thread executingThread = Thread.currentThread();

        @Override
        public boolean isCanceled() {
            return executingThread.isInterrupted();
        }

    }

}
