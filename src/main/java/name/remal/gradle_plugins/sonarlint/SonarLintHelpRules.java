package name.remal.gradle_plugins.sonarlint;

import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "This is a help task that only produces console output")
public abstract class SonarLintHelpRules
    extends AbstractSonarLintHelpTask<SonarLintHelpRulesWorkAction> {
}
