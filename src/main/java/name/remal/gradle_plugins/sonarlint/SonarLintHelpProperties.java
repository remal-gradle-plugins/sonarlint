package name.remal.gradle_plugins.sonarlint;

import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class SonarLintHelpProperties
    extends AbstractSonarLintHelpTask<SonarLintHelpPropertiesWorkAction> {
}
