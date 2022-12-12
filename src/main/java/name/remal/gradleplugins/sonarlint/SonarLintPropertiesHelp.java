package name.remal.gradleplugins.sonarlint;

import static name.remal.gradleplugins.sonarlint.internal.SonarLintCommand.HELP_PROPERTIES;

import name.remal.gradleplugins.sonarlint.internal.SonarLintCommand;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class SonarLintPropertiesHelp extends BaseSonarLintHelp {

    @Override
    protected SonarLintCommand getRunnerCommand() {
        return HELP_PROPERTIES;
    }

}
