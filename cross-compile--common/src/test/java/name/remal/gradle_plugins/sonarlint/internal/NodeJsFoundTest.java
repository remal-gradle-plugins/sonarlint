package name.remal.gradle_plugins.sonarlint.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class NodeJsFoundTest {

    @Test
    void serialization() throws Throwable {
        var original = NodeJsFound.builder()
            .executable(new File("qwe"))
            .version("asd")
            .build();

        final byte[] bytes;
        try (var outputStream = new ByteArrayOutputStream()) {
            try (var serializer = new ObjectOutputStream(outputStream)) {
                serializer.writeObject(original);
            }
            bytes = outputStream.toByteArray();
        }

        var deserializer = new ObjectInputStream(new ByteArrayInputStream(bytes));
        var deserialized = (NodeJsFound) deserializer.readObject();
        assertEquals(original, deserialized);
    }

}
