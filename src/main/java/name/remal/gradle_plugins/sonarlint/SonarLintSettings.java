package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.jspecify.annotations.Nullable;

public abstract class SonarLintSettings {

    @Input
    public abstract Property<Boolean> getIgnoreFailures();

    {
        getIgnoreFailures().convention(false);
    }


    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<SonarLintIssueSeverity> getFailOnSeverity();

    public void failOnSeverity(@Nullable Object severity) {
        if (severity == null) {
            getFailOnSeverity().unset();
            return;
        }

        getFailOnSeverity().set(SonarLintIssueSeverity.valueOf(severity.toString().toUpperCase()));
    }


    @Input
    public abstract Property<Boolean> getIsGeneratedCodeIgnored();

    {
        getIsGeneratedCodeIgnored().convention(true);
    }


    @Input
    @org.gradle.api.tasks.Optional
    public abstract ListProperty<String> getIgnoredPaths();


    @Nested
    public abstract SonarLintRulesSettings getRules();

    public void rules(Action<? super SonarLintRulesSettings> action) {
        action.execute(getRules());
    }


    @Input
    @org.gradle.api.tasks.Optional
    public abstract MapProperty<String, String> getSonarProperties();

    public void sonarProperty(String key, String value) {
        getSonarProperties().put(key, value);
    }

    public void sonarProperty(String key, Provider<String> value) {
        getSonarProperties().put(key, value);
    }


    @Nested
    public abstract SonarLintLoggingSettings getLogging();

    public void logging(Action<? super SonarLintLoggingSettings> action) {
        action.execute(getLogging());
    }


    @Console
    public abstract Property<Boolean> getCheckChangedCoreClasspath();

    {
        getCheckChangedCoreClasspath().convention(true);
    }

    @Console
    public abstract Property<Boolean> getFailOnChangedCoreClasspath();

    {
        getFailOnChangedCoreClasspath().convention(false);
    }


    @Nested
    public abstract SonarLintForkSettings getFork();

    public void fork(Action<? super SonarLintForkSettings> action) {
        action.execute(getFork());
    }

}
