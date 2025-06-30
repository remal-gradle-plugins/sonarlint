package name.remal.gradle_plugins.sonarlint.server;

import static java.lang.Integer.parseInt;
import static java.lang.Math.max;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsRunnable;
import static name.remal.gradle_plugins.toolkit.ThrowableUtils.unwrapReflectionException;

import com.google.common.base.Splitter;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintService;
import name.remal.gradle_plugins.sonarlint.server.api.ServerStartedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("java:S106")
class Server {

    private static final Duration SOCKET_IO_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(30);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofMinutes(15);

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    @SuppressWarnings("FutureReturnValueIgnored")
    public static void main(String[] args) throws Exception {
        var pluginFiles = Splitter.on(File.pathSeparatorChar).splitToStream(args[0])
            .filter(not(String::isEmpty))
            .map(File::new)
            .collect(toUnmodifiableList());
        var expectedAuthToken = args[1];
        var reportBackHost = args[2];
        var reportBackPort = parseInt(args[3]);

        var handlersExecutor = newCachedThreadPool(HANDLER_THREADS_FACTORY);
        try (
            var service = SonarLintService.builder()
                .pluginFiles(pluginFiles)
                .build()
        ) {
            try (var serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
                var shutdown = new ServerShutdown();
                var lastRequestNanos = new AtomicLong(nanoTime());
                Runnable acceptor = sneakyThrowsRunnable(() -> {
                    while (!shutdown.isShutdown()) {
                        var socket = serverSocket.accept();
                        socket.setSoTimeout(toIntExact(SOCKET_IO_TIMEOUT.toMillis()));
                        lastRequestNanos.set(nanoTime());

                        var connectionHandler = new ConnectionHandler(
                            socket,
                            expectedAuthToken,
                            service,
                            shutdown
                        );
                        handlersExecutor.submit(connectionHandler);
                    }
                });

                var quietShutdownThread = startQuietShutdownThread(lastRequestNanos, shutdown);
                shutdown.addShutdownAction(quietShutdownThread::interrupt);

                var acceptorThread = new Thread(acceptor);
                acceptorThread.setUncaughtExceptionHandler(Server::handleUncaughtException);
                acceptorThread.setName(Server.class.getSimpleName() + "-acceptor");
                acceptorThread.start();
                shutdown.addShutdownAction(acceptorThread::interrupt);

                reportBack(
                    reportBackHost,
                    reportBackPort,
                    serverSocket
                );

                acceptorThread.join();

            }

        } finally {
            handlersExecutor.shutdown();
            if (!handlersExecutor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), MILLISECONDS)) {
                handlersExecutor.shutdownNow();
            }
        }
    }

    @SuppressWarnings("java:S2629")
    private static void handleUncaughtException(Thread thread, Throwable exception) {
        exception = unwrapReflectionException(exception);
        if (exception instanceof InterruptedException) {
            thread.interrupt();
            return;
        }

        logger.error("Uncaught exception" + exception, exception);
    }

    @SneakyThrows
    private static void reportBack(
        String reportBackHost,
        int reportBackPort,
        ServerSocket serverSocket
    ) {
        var message = ServerStartedMessage.builder()
            .host(serverSocket.getInetAddress().getHostAddress())
            .port(serverSocket.getLocalPort())
            .build();

        try (var socket = new Socket(reportBackHost, reportBackPort)) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(toIntExact(SOCKET_IO_TIMEOUT.toMillis()));
            try (
                var out = new ObjectOutputStream(socket.getOutputStream());
                var in = new ObjectInputStream(socket.getInputStream())
            ) {
                out.writeObject(message);
                out.flush();

                var response = in.readUTF();
                if (!"ack".equals(response)) {
                    throw new IllegalStateException("No ack on " + ServerStartedMessage.class.getSimpleName());
                }
            }
        }
    }

    @SuppressWarnings({"java:S2142", "BusyWait"})
    private static Thread startQuietShutdownThread(AtomicLong lastRequestNanos, ServerShutdown shutdown) {
        var sleepMillis = max(QUIET_PERIOD.toMillis() / 100, 1);
        var quietShutdown = new Thread(sneakyThrowsRunnable(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(sleepMillis);
                if (lastRequestNanos.get() + QUIET_PERIOD.toNanos() < nanoTime()) {
                    shutdown.shutdown();
                }
            }
        }));
        quietShutdown.setUncaughtExceptionHandler(Server::handleUncaughtException);
        quietShutdown.setName(Server.class.getSimpleName() + "-quietShutdown");
        quietShutdown.start();
        return quietShutdown;
    }


    private static final AtomicLong HANDLER_THREADS_COUNTER = new AtomicLong(0);

    private static final ThreadFactory HANDLER_THREADS_FACTORY = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setUncaughtExceptionHandler(Server::handleUncaughtException);
        thread.setName(format(
            "%s-%s",
            ConnectionHandler.class.getSimpleName(),
            HANDLER_THREADS_COUNTER.incrementAndGet()
        ));
        return thread;
    };

}
