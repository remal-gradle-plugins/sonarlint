package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.Math.toIntExact;
import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.time.Duration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
@Getter
@NoArgsConstructor(access = PRIVATE, force = true)
class RmiSocketFactory implements RMIClientSocketFactory, RMIServerSocketFactory, Serializable {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(30);

    private final InetAddress bindAddr;

    @Nullable
    private transient volatile Integer lastUsedPort;

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        var clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(getBindAddr(), port), toIntExact(CONNECTION_TIMEOUT.toMillis()));
        clientSocket.setSoTimeout(toIntExact(READ_TIMEOUT.toMillis()));
        lastUsedPort = clientSocket.getPort();
        return clientSocket;
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        var serverSocket = new ServerSocket(port, 0, getBindAddr());
        lastUsedPort = serverSocket.getLocalPort();
        return serverSocket;
    }

}
