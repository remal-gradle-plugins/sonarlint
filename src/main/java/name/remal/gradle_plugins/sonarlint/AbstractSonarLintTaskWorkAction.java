package name.remal.gradle_plugins.sonarlint;

import org.gradle.workers.WorkAction;

interface AbstractSonarLintTaskWorkAction<Params extends AbstractSonarLintTaskWorkActionParams>
    extends WorkAction<Params> {
}
