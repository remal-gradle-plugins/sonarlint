package name.remal.gradle_plugins.sonarlint.internal;

import static name.remal.gradle_plugins.sonarlint.internal.SourceFile.newSourceFileBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class SourceFileTest {

    @Test
    void serialization() throws Throwable {
        var originalObject = SerializationTestUtils.populateBuilderWithValues(newSourceFileBuilder()).build();

        final byte[] bytes;
        try (var bytesOutputStream = new ByteArrayOutputStream()) {
            try (var outputStream = new ObjectOutputStream(bytesOutputStream)) {
                outputStream.writeObject(originalObject);
            }
            bytes = bytesOutputStream.toByteArray();
        }

        Object deserializedObject;
        try (var bytesInputStream = new ByteArrayInputStream(bytes)) {
            try (var inputStream = new ObjectInputStream(bytesInputStream)) {
                deserializedObject = inputStream.readObject();
            }
        }

        assertThat(deserializedObject).isEqualTo(originalObject);
    }

}
