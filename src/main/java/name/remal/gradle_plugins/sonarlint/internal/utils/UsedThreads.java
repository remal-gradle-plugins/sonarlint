package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.reflect.Proxy.newProxyInstance;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import name.remal.gradle_plugins.toolkit.ProxyInvocationHandler;
import org.jetbrains.annotations.Unmodifiable;

public class UsedThreads {

    private final Map<Thread, Integer> usedThreadsCounter = new ConcurrentHashMap<>();

    @Unmodifiable
    public synchronized Collection<Thread> getUsedThreads() {
        var threads = usedThreadsCounter.keySet().toArray(new Thread[0]);
        return List.of(threads);
    }

    public synchronized void registerThread(Thread thread) {
        usedThreadsCounter.compute(thread, (__, prev) -> prev == null ? 1 : prev + 1);
    }

    public synchronized void unregisterThread(Thread thread) {
        usedThreadsCounter.computeIfPresent(thread, (__, prev) -> prev - 1);
        usedThreadsCounter.values().removeIf(count -> count <= 0);
    }


    public <T> T withRegisterThreadEveryCall(Class<T> interfaceClass, T object) {
        var invocationHandler = new ProxyInvocationHandler();
        invocationHandler.add(
            method -> method.getDeclaringClass() != Object.class,
            (proxy, method, args) -> {
                var thread = Thread.currentThread();
                registerThread(thread);
                try {
                    return method.invoke(object, args);
                } finally {
                    unregisterThread(thread);
                }
            }
        );
        return interfaceClass.cast(
            newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                invocationHandler
            )
        );
    }

}
