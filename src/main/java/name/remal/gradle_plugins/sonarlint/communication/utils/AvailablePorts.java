package name.remal.gradle_plugins.sonarlint.communication.utils;

import static lombok.AccessLevel.PRIVATE;

import java.net.InetAddress;
import java.net.ServerSocket;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = PRIVATE)
abstract class AvailablePorts {

    @SneakyThrows
    public static int getAvailablePort(InetAddress address) {
        try (var socket = new ServerSocket(0, 0, address)) {
            return socket.getLocalPort();
        }
    }

}
