package name.remal.gradle_plugins.sonarlint;

import static org.gradle.api.plugins.HelpTasksPlugin.HELP_GROUP;

import lombok.Getter;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand;
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
    protected abstract SonarLintCommand getRunnerCommand();

    @Getter
    @SuppressWarnings("checkstyle:MemberName")
    private final BaseSonarLintInternals $internals = getProject().getObjects().newInstance(
        BaseSonarLintInternals.class,
        this
    );

    @TaskAction
    public void execute() {
        BaseSonarLintActions.execute(this, getRunnerCommand(), null);
    }

}
