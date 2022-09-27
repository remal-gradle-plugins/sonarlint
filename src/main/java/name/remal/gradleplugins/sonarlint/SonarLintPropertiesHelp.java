package name.remal.gradleplugins.sonarlint;

import static name.remal.gradleplugins.sonarlint.shared.RunnerCommand.HELP_PROPERTIES;

import name.remal.gradleplugins.sonarlint.shared.RunnerCommand;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class SonarLintPropertiesHelp extends BaseSonarLintHelp {

    @Override
    protected RunnerCommand getRunnerCommand() {
        return HELP_PROPERTIES;
    }

}
