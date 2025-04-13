package name.remal.gradle_plugins.sonarlint;

import static org.gradle.api.plugins.HelpTasksPlugin.HELP_GROUP;

public abstract class AbstractSonarLintHelpTask<
    WorkAction extends AbstractSonarLintHelpWorkAction
    > extends AbstractSonarLintTask<SonarLintHelpWorkActionParams, WorkAction> {

    {
        setGroup(HELP_GROUP);
        setImpliesSubProjects(true);
        getOutputs().doNotCacheIf("Produces only non-cacheable console output", __ -> true);
        getOutputs().upToDateWhen(__ -> false);
    }

}
