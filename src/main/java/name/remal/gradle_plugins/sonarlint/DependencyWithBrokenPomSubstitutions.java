package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
abstract class DependencyWithBrokenPomSubstitutions {

    private static final Map<String, String> NOTATION_TO_FIXED_VERSION = ImmutableMap.of(
        "org.eclipse.platform:org.eclipse.core.contenttype:3.8.200", "3.8.100",
        "org.eclipse.platform:org.eclipse.equinox.preferences:3.10.100", "3.10.200"
    );

    @Nullable
    public static String getVersionWithFixedPom(String notation) {
        return NOTATION_TO_FIXED_VERSION.get(notation);
    }

}
