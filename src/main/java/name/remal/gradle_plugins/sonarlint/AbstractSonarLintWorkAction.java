package name.remal.gradle_plugins.sonarlint;

import org.gradle.workers.WorkAction;

interface AbstractSonarLintWorkAction<Params extends AbstractSonarLintWorkActionParams>
    extends WorkAction<Params> {
}
