package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
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
public class SonarDependency implements Comparable<SonarDependency> {

    @NonNull
    String group;

    @NonNull
    String name;

    @NonNull
    String version;


    @Nullable
    String classifier;


    public String getId() {
        return group + ':' + name;
    }


    @Getter(lazy = true)
    String notation = createNotation();

    private String createNotation() {
        var notation = new StringBuilder();
        notation.append(group).append(':').append(name).append(':').append(version);
        if (classifier != null) {
            notation.append(':').append(classifier);
        }
        return notation.toString();
    }


    @Override
    public String toString() {
        return getNotation();
    }


    @Override
    public int compareTo(SonarDependency other) {
        return getNotation().compareTo(other.getNotation());
    }

}
