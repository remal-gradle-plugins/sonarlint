package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;
import static lombok.EqualsAndHashCode.CacheStrategy.LAZY;

import java.io.Serializable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@Value
@Builder
@RequiredArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PRIVATE, force = true)
@EqualsAndHashCode(cacheStrategy = LAZY)
public class SonarResolvedDependency implements Serializable {

    @NonNull
    String moduleGroup;

    @NonNull
    String moduleName;

    @NonNull
    String moduleVersion;


    public String getNotation() {
        return moduleGroup + ':' + moduleName + ':' + moduleVersion;
    }

    @Override
    public String toString() {
        return getNotation();
    }

}
