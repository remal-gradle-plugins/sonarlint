package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrowsConsumer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = PRIVATE)
public abstract class LogMessageRenderer {

    public static final String SIMPLE_LOG_MESSAGE_DATE_FORMAT = "HH:mm:ss.SSS";

    @SneakyThrows
    @SuppressWarnings("JavaUtilDate")
    public static void renderLogMessageTo(LogMessage logMessage, Appendable buf) {
        buf.append(new SimpleDateFormat(SIMPLE_LOG_MESSAGE_DATE_FORMAT).format(new Date(logMessage.getTimestamp())));
        buf.append(' ');

        buf.append('[');
        buf.append(logMessage.getThreadName());
        buf.append("] ");

        buf.append(String.valueOf(logMessage.getLevel()));
        buf.append(' ');

        buf.append(logMessage.getLoggerName());
        buf.append(" - ");

        buf.append(logMessage.getMessage());

        logMessage.getCause().ifPresent(sneakyThrowsConsumer(cause -> {
            try (
                var writer = new StringWriter();
                var printWriter = new PrintWriter(writer)
            ) {
                printWriter.println();
                cause.printStackTrace(printWriter);
                printWriter.flush();
                buf.append(String.valueOf(writer));
            }
        }));
    }

    public static String renderLogMessage(LogMessage logMessage) {
        var buf = new StringBuilder();
        renderLogMessageTo(logMessage, buf);
        return buf.toString();
    }

}
