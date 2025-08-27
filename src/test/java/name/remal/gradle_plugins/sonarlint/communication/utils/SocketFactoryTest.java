package name.remal.gradle_plugins.sonarlint.communication.utils;

import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.deserializeFrom;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class SocketFactoryTest {

    @Test
    @SuppressWarnings("AddressSelection")
    void serialization() throws Exception {
        var socketFactory = new SocketFactory(InetAddress.getByName("127.0.0.1"));

        var bytes = serializeToBytes(socketFactory);
        var deserialized = deserializeFrom(bytes, SocketFactory.class);

        assertEquals(socketFactory.getBindAddr(), deserialized.getBindAddr());
    }

}
