package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.toolkit.ProxyInvocationHandler;
import org.jspecify.annotations.Nullable;

@NoArgsConstructor(access = PRIVATE)
public abstract class AopUtils {

    public static <T> T withWrappedCalls(
        Class<T> interfaceClass,
        T object,
        AroundAdvice aroundAdvice
    ) {
        var invocationHandler = new ProxyInvocationHandler();
        invocationHandler.add(
            method -> method.getDeclaringClass() != Object.class,
            (proxy, method, args) -> {
                var realMethod = new RealMethod() {
                    @Override
                    @Nullable
                    public Object call() throws Throwable {
                        return method.invoke(object, args);
                    }

                    @Override
                    public Method getMethod() {
                        return method;
                    }

                    @Override
                    public String toString() {
                        var paramsStr = stream(method.getParameterTypes())
                            .map(Type::getTypeName)
                            .collect(joining(",", "(", ")"));
                        return method.getDeclaringClass().getName()
                            + '.'
                            + method.getName()
                            + paramsStr;
                    }
                };
                return aroundAdvice.around(realMethod);
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

    @FunctionalInterface
    public interface AroundAdvice {

        @Nullable
        Object around(RealMethod realMethod) throws Throwable;

    }

    public interface RealMethod {

        @Nullable
        Object call() throws Throwable;

        Method getMethod();

        @Override
        String toString();

    }

}
