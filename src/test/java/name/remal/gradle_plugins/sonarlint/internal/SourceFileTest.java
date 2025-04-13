package name.remal.gradle_plugins.sonarlint.internal;

import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.deserializeFrom;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceFileTest {

    @Test
    void serialization() {
        var originalObject = SerializationTestUtils.populateBuilderWithValues(SourceFile.builder()).build();

        var bytes = serializeToBytes(originalObject);
        var deserializedObject = deserializeFrom(bytes, SourceFile.class);

        assertThat(deserializedObject)
            .isEqualTo(originalObject);
    }

}
