package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;
import static name.remal.gradle_plugins.toolkit.ThrowableUtils.unwrapException;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoArgsConstructor(access = PRIVATE, force = true)
public class SonarLintServerException extends RuntimeException {

    private static final Logger logger = LoggerFactory.getLogger(SonarLintServerException.class);

    SonarLintServerException(Throwable exception) {
        super(exception.toString());
        resetStackTrace();
    }

    public void resetStackTrace() {
        setStackTrace(new StackTraceElement[]{
            new StackTraceElement("see.server.logs.for", "details", "", -1)
        });
    }


    @SuppressWarnings("java:S2139")
    public static <T> T withServerExceptionCalls(Class<T> interfaceClass, T object) {
        return withWrappedCalls(interfaceClass, object, realMethod -> {
            try {
                return realMethod.call();

            } catch (Throwable exception) {
                exception = unwrapException(exception);
                logger.error(exception.toString(), exception);
                throw new SonarLintServerException(exception);
            }
        });
    }

}
