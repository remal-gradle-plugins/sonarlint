package name.remal.gradle_plugins.sonarlint;

import static lombok.Builder.Default;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@Builder
class RuleExampleParams {

    @Default
    String srcDir = "src/main/resources";

    @Nullable
    String fileExtension;

    @Nullable
    String relativeFilePath;

}
