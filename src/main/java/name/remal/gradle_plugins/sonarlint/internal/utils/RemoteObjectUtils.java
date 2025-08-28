package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;

import java.net.InetAddress;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = PRIVATE)
public abstract class RemoteObjectUtils {

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T extends Remote> T exportObject(T object, InetAddress bindAddr, int port) {
        var socketFactory = new RmiSocketFactory(bindAddr);
        return (T) UnicastRemoteObject.exportObject(object, port, socketFactory, socketFactory);
    }

    public static void unexportObject(Remote object) {
        try {
            UnicastRemoteObject.unexportObject(object, true);
        } catch (NoSuchObjectException e) {
            // do nothing
        }
    }

}
