package name.remal.gradle_plugins.sonarlint.internal.impl;

import static lombok.AccessLevel.PRIVATE;

import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

@CustomLog
@NoArgsConstructor(access = PRIVATE)
class NoOpProgressMonitor implements ProgressMonitor {

    public static final NoOpProgressMonitor NOOP_PROGRESS_MONITOR = new NoOpProgressMonitor();


    private final Thread executingThread = Thread.currentThread();

    @Override
    public boolean isCanceled() {
        return executingThread.isInterrupted();
    }


    @Override
    public void notifyProgress(@Nullable String message, @Nullable Integer percentage) {
        // do nothing
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
