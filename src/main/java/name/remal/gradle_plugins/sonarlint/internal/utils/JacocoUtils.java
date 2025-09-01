package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;

import javax.management.ObjectName;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

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
        return withWrappedCalls(interfaceClass, object, realMethod -> {
            try {
                return realMethod.call();
            } finally {
                dumpJacocoData();
            }
        });
    }

}
