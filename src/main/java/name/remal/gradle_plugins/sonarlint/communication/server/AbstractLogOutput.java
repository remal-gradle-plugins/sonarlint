package name.remal.gradle_plugins.sonarlint.communication.server;

import static java.util.Collections.newSetFromMap;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.DEBUG;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.ERROR;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.OFF;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.TRACE;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.WARN;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;

abstract class AbstractLogOutput implements LogOutput {

    protected abstract void logImpl(String formattedMessage, org.slf4j.event.Level slf4jLevel);


    private final Set<String> loggedMessages = newSetFromMap(new ConcurrentHashMap<>());

    private static final Map<Pattern, Level> MESSAGE_LEVELS = ImmutableMap.<Pattern, Level>builder()
        .put(
            Pattern.compile("No workDir in SonarLint"),
            TRACE
        )
        .put(
            Pattern.compile(".+\\. Enable DEBUG mode to see them\\."),
            TRACE
        )
        .put(
            Pattern.compile("Plugin '[^']+' is excluded because"
                + " (none of languages '[^']+' are|language '[^']+' is not) enabled"
                + "\\. Skip loading it\\."
            ),
            DEBUG
        )
        .put(
            Pattern.compile("Plugin '[^']+' is excluded .+\\. Skip loading it\\."),
            WARN
        )
        .put(
            Pattern.compile("Your platform is not supported for embedded.*"),
            WARN
        )
        .put(
            Pattern.compile("Embedded node not found for platform.*"),
            WARN
        )
        .build();

    @Override
    public final void log(@Nullable String formattedMessage, Level level, @Nullable String stacktrace) {
        if (level == OFF) {
            return;
        }

        var message = Stream.of(formattedMessage, stacktrace)
            .filter(Objects::nonNull)
            .filter(not(String::isBlank))
            .collect(joining("\n"));

        message = message.trim();
        if (message.isEmpty()) {
            return;
        }

        if (!loggedMessages.add(message)) {
            return;
        }

        for (var entry : MESSAGE_LEVELS.entrySet()) {
            if (entry.getKey().matcher(message).matches()) {
                level = entry.getValue();
                break;
            }
        }

        final org.slf4j.event.Level slf4jLevel;
        if (level == OFF) {
            return;
        } else if (level == ERROR) {
            slf4jLevel = org.slf4j.event.Level.ERROR;
        } else if (level == WARN) {
            slf4jLevel = org.slf4j.event.Level.WARN;
        } else if (level == DEBUG) {
            slf4jLevel = org.slf4j.event.Level.DEBUG;
        } else if (level == TRACE) {
            slf4jLevel = org.slf4j.event.Level.TRACE;
        } else {
            slf4jLevel = org.slf4j.event.Level.INFO;
        }

        logImpl(message, slf4jLevel);
    }

    @Override
    @SuppressWarnings("deprecation")
    public final void log(String formattedMessage, Level level) {
        log(formattedMessage, level, null);
    }

}
