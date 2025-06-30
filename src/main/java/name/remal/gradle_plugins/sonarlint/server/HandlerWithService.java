package name.remal.gradle_plugins.sonarlint.server;

import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintService;

interface HandlerWithService<T> {

    T withService(SonarLintService service);

}
