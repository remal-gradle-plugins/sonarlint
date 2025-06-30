package name.remal.gradle_plugins.sonarlint.server;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ServerShutdown {

    private static final Logger logger = LoggerFactory.getLogger(ServerShutdown.class);


    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    private final Deque<ShutdownAction> shutdownActions = new ConcurrentLinkedDeque<>();

    public void addShutdownAction(ShutdownAction shutdownAction) {
        if (isShutdown.get()) {
            throw new IllegalStateException("Already shutdown");
        }

        shutdownActions.addLast(shutdownAction);
    }

    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            while (true) {
                var action = shutdownActions.pollLast();
                if (action == null) {
                    break;
                }

                try {
                    action.execute();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("Uncaught exception" + e, e);
                }
            }
        }
    }

    public boolean isShutdown() {
        return isShutdown.get();
    }


    public interface ShutdownAction {
        void execute() throws Exception;
    }

}
