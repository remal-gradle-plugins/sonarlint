package name.remal.gradle_plugins.sonarlint;

import static org.gradle.api.plugins.HelpTasksPlugin.HELP_GROUP;

import name.remal.gradle_plugins.sonarlint.settings.WithSonarLintForkSettings;

public abstract class AbstractSonarLintHelp<
    WorkActionParams extends AbstractSonarLintWorkActionParams,
    WorkAction extends AbstractSonarLintWorkAction<WorkActionParams>
    >
    extends AbstractSonarLint<WorkActionParams, WorkAction>
    implements WithSonarLintForkSettings {

    {
        getOutputs().doNotCacheIf("Produces only non-cacheable console output", __ -> true);
        setGroup(HELP_GROUP);
        setImpliesSubProjects(true);
    }

}
