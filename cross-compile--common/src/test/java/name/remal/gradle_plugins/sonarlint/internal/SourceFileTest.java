package name.remal.gradle_plugins.sonarlint.internal;

import static name.remal.gradle_plugins.sonarlint.internal.SerializationTestUtils.populateBuilderWithValues;
import static name.remal.gradle_plugins.sonarlint.internal.SourceFile.newSourceFileBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import lombok.val;
import org.junit.jupiter.api.Test;

class SourceFileTest {

    @Test
    void serialization() throws Throwable {
        val originalObject = populateBuilderWithValues(newSourceFileBuilder()).build();

        final byte[] bytes;
        try (val bytesOutputStream = new ByteArrayOutputStream()) {
            try (val outputStream = new ObjectOutputStream(bytesOutputStream)) {
                outputStream.writeObject(originalObject);
            }
            bytes = bytesOutputStream.toByteArray();
        }

        Object deserializedObject;
        try (val bytesInputStream = new ByteArrayInputStream(bytes)) {
            try (val inputStream = new ObjectInputStream(bytesInputStream)) {
                deserializedObject = inputStream.readObject();
            }
        }

        assertThat(deserializedObject).isEqualTo(originalObject);
    }

}
