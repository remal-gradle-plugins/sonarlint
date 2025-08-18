package name.remal.gradle_plugins.sonarlint;

import static lombok.Builder.Default;

import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

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
