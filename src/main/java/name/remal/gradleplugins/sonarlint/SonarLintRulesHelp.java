package name.remal.gradleplugins.sonarlint;

import static name.remal.gradleplugins.sonarlint.shared.RunnerCommand.HELP_RULES;

import name.remal.gradleplugins.sonarlint.shared.RunnerCommand;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class SonarLintRulesHelp extends BaseSonarLintHelp {

    @Override
    protected RunnerCommand getRunnerCommand() {
        return HELP_RULES;
    }

}
