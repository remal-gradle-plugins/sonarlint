package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static org.slf4j.event.Level.WARN;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.event.Level;

@NoArgsConstructor(access = PRIVATE)
public class SimpleLoggingEventBuilder {

    private static final boolean REWRITE_LOGGING_LEVEL_FOR_TESTS = false;
    private static final Level MIN_LOGGING_LEVEL_FOR_TESTS = WARN;


    @CheckReturnValue
    public static MessageBuilder newLoggingEvent(Level level) {
        if (REWRITE_LOGGING_LEVEL_FOR_TESTS
            && MIN_LOGGING_LEVEL_FOR_TESTS.toInt() > level.toInt()
        ) {
            level = MIN_LOGGING_LEVEL_FOR_TESTS;
        }

        return new Impl(level);
    }


    public interface MessageBuilder {
        @CheckReturnValue
        CauseBuilder message(String message);

        @FormatMethod
        @CheckReturnValue
        CauseBuilder message(@FormatString String message, @Nullable Object... args);
    }

    public interface CauseBuilder extends WithLog {
        @CheckReturnValue
        WithLog cause(@Nullable Throwable cause);
    }

    public interface WithLog {
        void log(Logger logger);

        void log(AccumulatingLoggerMethods logger);
    }


    @RequiredArgsConstructor
    @ToString
    private static class Impl
        implements WithLog, MessageBuilder, CauseBuilder {

        private final Level level;

        @Nullable
        private String message;

        @Nullable
        private Throwable cause;

        @Override
        @SuppressWarnings("java:S3776")
        public void log(Logger logger) {
            var level = requireNonNull(this.level, "level");
            var message = requireNonNull(this.message, "message");
            switch (level) {
                case ERROR:
                    if (cause == null) {
                        logger.error(message);
                    } else {
                        logger.error(message, cause);
                    }
                    break;
                case WARN:
                    if (cause == null) {
                        logger.warn(message);
                    } else {
                        logger.warn(message, cause);
                    }
                    break;
                case DEBUG:
                    if (cause == null) {
                        logger.debug(message);
                    } else {
                        logger.debug(message, cause);
                    }
                    break;
                case TRACE:
                    if (cause == null) {
                        logger.trace(message);
                    } else {
                        logger.trace(message, cause);
                    }
                    break;
                default:
                    if (cause == null) {
                        logger.info(message);
                    } else {
                        logger.info(message, cause);
                    }
            }
        }

        @Override
        public void log(AccumulatingLoggerMethods logger) {
            var level = requireNonNull(this.level, "level");
            var message = requireNonNull(this.message, "message");
            logger.log(level, message, cause);
        }

        @Override
        @CheckReturnValue
        public Impl message(String message) {
            this.message = message;
            return this;
        }

        @Override
        @FormatMethod
        @CheckReturnValue
        public Impl message(@FormatString String message, @Nullable Object... args) {
            return message(format(message, args));
        }

        @Override
        @CheckReturnValue
        public Impl cause(@Nullable Throwable cause) {
            this.cause = cause;
            return this;
        }

    }

}
