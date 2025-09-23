package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.util.Collections.synchronizedList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class AccumulatingLoggerContext {

    public final List<LogMessage> logMessages = synchronizedList(new ArrayList<>());

    public final AtomicInteger hiddenMessagesCounter = new AtomicInteger();

}
