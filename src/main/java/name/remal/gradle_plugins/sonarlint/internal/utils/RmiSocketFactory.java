package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
@NoArgsConstructor(access = PRIVATE, force = true)
class RmiSocketFactory implements RMIClientSocketFactory, RMIServerSocketFactory, Serializable {

    private final InetAddress bindAddr;

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return new Socket(getBindAddr(), port);
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        return new ServerSocket(port, 0, getBindAddr());
    }

}
