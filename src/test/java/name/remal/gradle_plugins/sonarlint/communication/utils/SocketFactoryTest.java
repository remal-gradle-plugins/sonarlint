package name.remal.gradle_plugins.sonarlint.communication.utils;

import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.deserializeFrom;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SocketFactoryTest {

    @Test
    void serialization() {
        var serverSocketFactory = new SocketFactory();

        var bindAddr = serverSocketFactory.getBindAddr();

        var bytes = serializeToBytes(serverSocketFactory);
        var deserialized = deserializeFrom(bytes, SocketFactory.class);

        assertEquals(bindAddr, deserialized.getBindAddr());
    }

}
