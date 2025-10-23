package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;
import static name.remal.gradle_plugins.sonarlint.internal.utils.LogMessageRenderer.renderLogMessageTo;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class AccumulatingLogger implements AccumulatingLoggerMethods {

    private static final int MESSAGES_TO_STORE = 1_000;


    private final Logger delegate;

    private AccumulatingLogger(Logger delegate) {
        this.delegate = delegate;
    }

    public AccumulatingLogger(String loggerName) {
        this(LoggerFactory.getLogger(loggerName));
    }

    public AccumulatingLogger(Class<?> clazz) {
        this(clazz.getName());
    }


    private static final ThreadLocal<AccumulatingLoggerContext> CONTEXT =
        ThreadLocal.withInitial(AccumulatingLoggerContext::new);

    @Unmodifiable
    public Collection<LogMessage> getStoredLogMessages() {
        return List.of(CONTEXT.get().logMessages.toArray(new LogMessage[0]));
    }

    public int getHiddenMessagesCounter() {
        return CONTEXT.get().hiddenMessagesCounter.get();
    }

    public void reset() {
        CONTEXT.remove();
    }


    @Override
    @FormatMethod
    public void log(Level level, @FormatString String format, @Nullable Object... args) {
        newLoggingEvent(level).message(format, args).log(delegate);
        storeMessage(level, format(format, args), null);
    }

    @Override
    public void log(Level level, String message, @Nullable Throwable cause) {
        newLoggingEvent(level).message(message).cause(cause).log(delegate);
        storeMessage(level, message, cause);
    }


    private void storeMessage(Level level, String message, @Nullable Throwable cause) {
        var context = CONTEXT.get();
        synchronized (context) {
            if (context.logMessages.size() >= MESSAGES_TO_STORE) {
                context.hiddenMessagesCounter.incrementAndGet();
                return;
            }

            context.logMessages.add(ImmutableLogMessage.builder()
                .loggerName(delegate.getName())
                .level(level)
                .message(message)
                .cause(cause)
                .build());
        }
    }

    private static final ThreadLocal<@Nullable Boolean> WRAPPED = new ThreadLocal<>();

    public <T> T wrapCalls(Class<T> interfaceClass, T object) {
        var context = CONTEXT.get();
        return withWrappedCalls(interfaceClass, object, realMethod -> {
            var wrapped = WRAPPED.get();
            if (TRUE.equals(wrapped)) {
                return realMethod.call();
            }

            CONTEXT.set(context);
            WRAPPED.set(TRUE);
            try {
                return realMethod.call();
            } finally {
                WRAPPED.remove();
                CONTEXT.remove();
            }
        });
    }

    public String render() {
        var buf = new StringBuilder();

        var context = CONTEXT.get();
        for (var message : context.logMessages.toArray(new LogMessage[0])) {
            if (buf.length() > 0) {
                buf.append(lineSeparator());
            }

            renderLogMessageTo(message, buf);
        }

        var hiddenMessagesCounter = context.hiddenMessagesCounter.get();
        if (hiddenMessagesCounter > 0) {
            if (buf.length() > 0) {
                buf.append(lineSeparator());
            }

            buf.append("... ").append(hiddenMessagesCounter).append(" hidden messages ...");
        }

        return buf.toString();
    }

}
