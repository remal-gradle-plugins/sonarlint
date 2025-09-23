package name.remal.gradle_plugins.sonarlint.internal.utils;

import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

public interface AccumulatingLoggerMethods {

    @FormatMethod
    void log(Level level, @FormatString String format, @Nullable Object... args);

    void log(Level level, String message, @Nullable Throwable cause);

    default void log(Level level, String message) {
        log(level, message, (Throwable) null);
    }


    @FormatMethod
    default void trace(@FormatString String format, @Nullable Object... args) {
        log(TRACE, format, args);
    }

    default void trace(String message, Throwable exception) {
        log(TRACE, message, exception);
    }

    @FormatMethod
    default void debug(@FormatString String format, @Nullable Object... args) {
        log(DEBUG, format, args);
    }

    default void debug(String message, Throwable exception) {
        log(DEBUG, message, exception);
    }

    @FormatMethod
    default void info(@FormatString String format, @Nullable Object... args) {
        log(INFO, format, args);
    }

    default void info(String message, Throwable exception) {
        log(INFO, message, exception);
    }

    @FormatMethod
    default void warn(@FormatString String format, @Nullable Object... args) {
        log(WARN, format, args);
    }

    default void warn(String message, Throwable exception) {
        log(WARN, message, exception);
    }

    @FormatMethod
    default void error(@FormatString String format, @Nullable Object... args) {
        log(ERROR, format, args);
    }

    default void error(String message, Throwable exception) {
        log(ERROR, message, exception);
    }

}
