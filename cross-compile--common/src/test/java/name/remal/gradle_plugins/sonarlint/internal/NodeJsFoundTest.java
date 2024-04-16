package name.remal.gradle_plugins.sonarlint.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import lombok.val;
import org.junit.jupiter.api.Test;

class NodeJsFoundTest {

    @Test
    void serialization() throws Throwable {
        val original = NodeJsFound.builder()
            .executable(new File("qwe"))
            .version("asd")
            .build();

        final byte[] bytes;
        try (val outputStream = new ByteArrayOutputStream()) {
            try (val serializer = new ObjectOutputStream(outputStream)) {
                serializer.writeObject(original);
            }
            bytes = outputStream.toByteArray();
        }

        val deserializer = new ObjectInputStream(new ByteArrayInputStream(bytes));
        val deserialized = (NodeJsFound) deserializer.readObject();
        assertEquals(original, deserialized);
    }

}
