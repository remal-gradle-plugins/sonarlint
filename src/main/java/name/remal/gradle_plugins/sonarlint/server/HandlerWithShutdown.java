package name.remal.gradle_plugins.sonarlint.server;

interface HandlerWithShutdown<T> {

    T withShutdown(ServerShutdown shutdown);

}
