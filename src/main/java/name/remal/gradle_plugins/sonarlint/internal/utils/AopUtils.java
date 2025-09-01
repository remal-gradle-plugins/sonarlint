package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.reflect.Proxy.newProxyInstance;
import static lombok.AccessLevel.PRIVATE;

import java.lang.reflect.Method;
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
                        return method.toString();
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
