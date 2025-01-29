package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.deserializeFrom;
import static name.remal.gradle_plugins.toolkit.JavaSerializationUtils.serializeToBytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SonarDependencyTest {

    @Test
    void serialization() {
        var source = SonarDependency.builder()
            .group("group")
            .name("name")
            .version("version")
            .classifier("classifier")
            .build();

        var bytes = serializeToBytes(source);
        var deserialized = deserializeFrom(bytes, SonarDependency.class);

        assertThat(deserialized)
            .isNotSameAs(source)
            .isEqualTo(source);
    }

}
