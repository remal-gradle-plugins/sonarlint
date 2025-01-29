package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import javax.annotation.Nullable;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@Value
@Builder
@With
@RequiredArgsConstructor(access = PRIVATE)
public class SonarDependency {

    @NonNull
    String group;

    @NonNull
    String name;

    @NonNull
    String version;


    @Nullable
    String classifier;


    public String getNotation() {
        var notation = group + ':' + name + ':' + version;
        if (classifier != null) {
            notation += ':' + classifier;
        }
        return notation;
    }

    public String getId() {
        return group + ':' + name;
    }


    @Override
    public String toString() {
        return getNotation();
    }

}
