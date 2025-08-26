package name.remal.gradle_plugins.sonarlint.communication.server.api;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.issues.Issue;

@SuppressWarnings("java:S107")
public interface SonarLintAnalyze extends Remote {

    Collection<Issue> analyze(
        File repositoryRoot,
        String moduleId,
        Collection<SourceFile> sourceFiles,
        Set<SonarLintLanguage> enabledLanguages,
        Map<String, String> sonarProperties,
        boolean enableRulesActivatedByDefault,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig,
        SonarLintLogSink logSink
    ) throws RemoteException;

}
