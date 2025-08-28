package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static java.lang.reflect.Proxy.newProxyInstance;
import static lombok.AccessLevel.PRIVATE;

import javax.management.ObjectName;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.toolkit.ProxyInvocationHandler;

@NoArgsConstructor(access = PRIVATE)
public abstract class JacocoUtils {

    @SneakyThrows
    public static void dumpJacocoData() {
        var mbeanServer = getPlatformMBeanServer();
        var jacocoObjectName = ObjectName.getInstance("org.jacoco:type=Runtime");
        if (mbeanServer.isRegistered(jacocoObjectName)) {
            mbeanServer.invoke(
                jacocoObjectName,
                "dump",
                new Object[]{true},
                new String[]{"boolean"}
            );
        }
    }

    public static <T> T withDumpJacocoDataOnEveryCall(Class<T> interfaceClass, T object) {
        var invocationHandler = new ProxyInvocationHandler();
        invocationHandler.add(
            method -> method.getDeclaringClass() != Object.class,
            (proxy, method, args) -> {
                try {
                    return method.invoke(object, args);
                } finally {
                    dumpJacocoData();
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
