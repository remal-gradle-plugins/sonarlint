package name.remal.gradle_plugins.sonarlint.internal.server.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("java:S107")
public interface SonarLintAnalyzer extends Remote {

    Collection<Issue> analyze(
        SonarLintAnalyzeParams params,
        @Nullable SonarLintLogSink logSink
    ) throws RemoteException;

}
