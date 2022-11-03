package name.remal.gradleplugins.sonarlint;

import static org.gradle.api.plugins.HelpTasksPlugin.HELP_GROUP;

import name.remal.gradleplugins.sonarlint.shared.RunnerCommand;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

abstract class BaseSonarLintHelp
    extends DefaultTask
    implements BaseSonarLint {

    {
        getOutputs().doNotCacheIf("Non a cacheable task", __ -> true);
        setGroup(HELP_GROUP);
        setImpliesSubProjects(true);
        BaseSonarLintActions.init(this);
    }

    @Internal
    protected abstract RunnerCommand getRunnerCommand();

    @TaskAction
    public void execute() {
        BaseSonarLintActions.execute(this, getRunnerCommand());
    }

}
