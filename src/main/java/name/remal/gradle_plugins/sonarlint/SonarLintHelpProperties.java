package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.tasks.UntrackedTask;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "This is a help task that only produces console output")
@UntrackedTask(because = "This is a help task that only produces console output")
public abstract class SonarLintHelpProperties
    extends AbstractSonarLintHelpTask<SonarLintHelpPropertiesWorkAction> {
}
