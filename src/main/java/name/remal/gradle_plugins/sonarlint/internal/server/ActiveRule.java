package name.remal.gradle_plugins.sonarlint.internal.server;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(
    visibility = ImplementationVisibility.SAME,
    builderVisibility = BuilderVisibility.PUBLIC,
    jdkOnly = true,
    get = "*",
    optionalAcceptNullable = true,
    privateNoargConstructor = true,
    typeBuilder = "*Builder",
    typeInnerBuilder = "BaseBuilder",
    allowedClasspathAnnotations = {
        org.immutables.value.Generated.class,
        Nullable.class,
        javax.annotation.Nullable.class,
        Immutable.class,
        ThreadSafe.class,
        NotThreadSafe.class,
    },
    depluralize = true
)
@SuppressWarnings("java:S2176")
interface ActiveRule extends org.sonar.api.batch.rule.ActiveRule {

    @Override
    @Nullable
    default String param(String key) {
        return params().get(key);
    }

    @Value.NonAttribute
    default String qpKey() {
        throw new UnsupportedOperationException("qpKey not supported in SonarLint");
    }

}
