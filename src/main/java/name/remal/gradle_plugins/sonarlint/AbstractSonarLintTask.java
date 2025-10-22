package name.remal.gradle_plugins.sonarlint;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static lombok.EqualsAndHashCode.CacheStrategy.LAZY;
import static name.remal.gradle_plugins.build_time_constants.api.BuildTimeConstants.getStringProperty;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.SONARLINT_CORE_LOGGING_RESOLVED_DEPENDENCIES;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.SONARLINT_CORE_RESOLVED_DEPENDENCIES;
import static name.remal.gradle_plugins.sonarlint.SonarLintConstants.MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getEnvironmentVariablesToPropagateToForkedProcess;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getEnvironmentVariablesToSetToForkedProcess;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getSystemsPropertiesToPropagateToForkedProcess;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getSystemsPropertiesToSetToForkedProcess;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.VisibleForTesting;

@CacheableTask
public abstract class AbstractSonarLintTask
    extends DefaultTask {

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getCoreClasspath();

    @InputFiles
    @Classpath
    @org.gradle.api.tasks.Optional
    public abstract ConfigurableFileCollection getCoreLoggingClasspath();

    @InputFiles
    @Classpath
    @org.gradle.api.tasks.Optional
    public abstract ConfigurableFileCollection getPluginFiles();

    @Nested
    public abstract SonarLintSettings getSettings();

    @Nested
    protected abstract SonarLintLanguagesSettings getLanguages();


    @Internal
    protected abstract Property<Boolean> getIsForkEnabled();

    {
        getIsForkEnabled().value(getProviders().provider(() -> {
            var forkOptions = getSettings().getFork();

            boolean isForkEnabled = forkOptions.getEnabled().get();
            if (!isForkEnabled
                && JavaVersion.current().compareTo(MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION) < 0
            ) {
                getLogger().warn(
                    "The current Java version ({}) is less than required for SonarLint {}."
                        + " Enabling forking for task {}.",
                    JavaVersion.current().getMajorVersion(),
                    MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION.getMajorVersion(),
                    getPath()
                );
                isForkEnabled = true;
            }

            return isForkEnabled;
        })).finalizeValueOnRead();
    }

    protected final WorkQueue createWorkQueue() {
        if (getIsForkEnabled().getOrElse(false)) {
            return getWorkerExecutor().processIsolation(spec -> {
                var forkOptions = getSettings().getFork();
                var javaLauncher = forkOptions.getJavaLauncher().get();
                spec.getForkOptions().setExecutable(javaLauncher.getExecutablePath().getAsFile().getAbsolutePath());


                spec.getForkOptions().environment(getEnvironmentVariablesToSetToForkedProcess());

                getEnvironmentVariablesToPropagateToForkedProcess().forEach(envVar -> {
                    var value = System.getenv(envVar);
                    if (value != null) {
                        spec.getForkOptions().environment(envVar, value);
                    }
                });


                spec.getForkOptions().setMaxHeapSize(forkOptions.getMaxHeapSize().getOrNull());

                if (javaLauncher.getMetadata().getLanguageVersion().canCompileOrRun(9)) {
                    spec.getForkOptions().jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED");
                }


                getSystemsPropertiesToSetToForkedProcess().forEach((property, value) -> {
                    spec.getForkOptions().jvmArgs(format("-D%s=%s", property, value));
                });

                getSystemsPropertiesToPropagateToForkedProcess().forEach(property -> {
                    var value = System.getProperty(property);
                    if (value != null) {
                        spec.getForkOptions().jvmArgs(format("-D%s=%s", property, value));
                    }
                });


                spec.getClasspath().from(getCoreClasspath());
            });

        } else {
            return getWorkerExecutor().classLoaderIsolation(spec -> {
                spec.getClasspath().from(getCoreClasspath());
            });
        }
    }


    @Input
    @org.gradle.api.tasks.Optional
    protected abstract SetProperty<SonarResolvedDependency> getCoreResolvedDependencies();

    @Input
    @org.gradle.api.tasks.Optional
    protected abstract SetProperty<SonarResolvedDependency> getCoreLoggingResolvedDependencies();

    protected final void checkCoreResolvedDependencies() {
        boolean checkChangedCoreClasspath = getSettings().getCheckChangedCoreClasspath().get();
        if (!checkChangedCoreClasspath) {
            return;
        }

        boolean failOnChangedCoreClasspath = getSettings().getFailOnChangedCoreClasspath().get();

        checkResolvedDependencies(
            "core",
            ImmutableSet.copyOf(SONARLINT_CORE_RESOLVED_DEPENDENCIES),
            getCoreResolvedDependencies().get(),
            failOnChangedCoreClasspath
        );
        checkResolvedDependencies(
            "core logging",
            ImmutableSet.copyOf(SONARLINT_CORE_LOGGING_RESOLVED_DEPENDENCIES),
            getCoreLoggingResolvedDependencies().get(),
            failOnChangedCoreClasspath
        );
    }

    @SuppressWarnings("java:S3776")
    private void checkResolvedDependencies(
        String scope,
        Set<SonarResolvedDependency> expected,
        Set<SonarResolvedDependency> actual,
        boolean failOnChangedCoreClasspath
    ) {
        if (expected.equals(actual)) {
            return;
        }

        var changes = collectDependencyChanges(expected, actual);
        var missingDependencies = changes.missingDependencies;
        var changedVersions = changes.changedVersions;
        var unexpectedDependencies = changes.unexpectedDependencies;

        if (missingDependencies.isEmpty()
            && changedVersions.isEmpty()
            && unexpectedDependencies.isEmpty()
        ) {
            return;
        }


        var buf = new StringBuilder();
        Supplier<StringBuilder> withNewLineIfNeeded = () -> {
            if (buf.length() > 0) {
                buf.append(lineSeparator());
            }
            return buf;
        };

        withNewLineIfNeeded.get()
            .append("Unexpected SonarLint ").append(scope).append(" classpath. It can cause SonarLint issues.");

        withNewLineIfNeeded.get()
            .append("It's likely that an applied plugin (or the build logic) changed SonarLint dependencies.");

        withNewLineIfNeeded.get()
            .append("A common candidate is the `io.spring.dependency-management` plugin.")
            .append(" This plugin manages versions in all configurations and changes SonarLint dependencies.")
            .append(" (see ").append(getStringProperty("repository.html-url")).append("/issues/643).")
            .append(" It's recommended to use constraints to manage versions.");

        if (!missingDependencies.isEmpty()) {
            withNewLineIfNeeded.get()
                .append("Missing ").append(scope).append(" dependencies:");
            missingDependencies.forEach(dep -> withNewLineIfNeeded.get().append("  ").append(dep));
        }

        if (!changedVersions.isEmpty()) {
            withNewLineIfNeeded.get()
                .append("Changed ").append(scope).append(" versions:");
            changedVersions.forEach((id, change) ->
                withNewLineIfNeeded.get().append("  ").append(id).append(": ")
                    .append(change.expectedVersion).append(" -> ").append(change.actualVersion)
            );
        }

        if (!unexpectedDependencies.isEmpty()) {
            withNewLineIfNeeded.get()
                .append("Unexpected ").append(scope).append(" dependencies:");
            unexpectedDependencies.forEach(dep -> withNewLineIfNeeded.get().append("  ").append(dep));
        }

        if (failOnChangedCoreClasspath) {
            withNewLineIfNeeded.get()
                .append("To make this message a warning,")
                .append(" add `sonarLint.failOnChangedCoreClasspath = false` to your build script.");
        }

        withNewLineIfNeeded.get()
            .append("To hide this message,")
            .append(" add `sonarLint.checkChangedCoreClasspath = false` to your build script.");

        if (failOnChangedCoreClasspath) {
            throw new GradleException(buf.toString());
        } else {
            getLogger().warn("{}", buf);
        }
    }

    @VisibleForTesting
    static DependencyChanges collectDependencyChanges(
        Set<SonarResolvedDependency> expected,
        Set<SonarResolvedDependency> actual
    ) {
        var changes = new DependencyChanges();

        var expectedVersions = expected.stream()
            .collect(toImmutableMap(
                dep -> new SonarResolvedDependencyId(dep.getModuleGroup(), dep.getModuleName()),
                SonarResolvedDependency::getModuleVersion
            ));

        var actualVersions = actual.stream()
            .collect(toImmutableMap(
                dep -> new SonarResolvedDependencyId(dep.getModuleGroup(), dep.getModuleName()),
                SonarResolvedDependency::getModuleVersion
            ));

        expectedVersions.forEach((id, expectedVersion) -> {
            var actualVersion = actualVersions.get(id);
            if (actualVersion == null) {
                changes.missingDependencies.add(
                    SonarResolvedDependency.builder()
                        .moduleGroup(id.moduleGroup)
                        .moduleName(id.moduleName)
                        .moduleVersion(expectedVersion)
                        .build()
                );

            } else if (!expectedVersion.equals(actualVersion)) {
                changes.changedVersions.put(id, new VersionChange(expectedVersion, actualVersion));
            }
        });

        actualVersions.forEach((id, version) -> {
            if (!expectedVersions.containsKey(id)) {
                changes.unexpectedDependencies.add(
                    SonarResolvedDependency.builder()
                        .moduleGroup(id.moduleGroup)
                        .moduleName(id.moduleName)
                        .moduleVersion(version)
                        .build()
                );
            }
        });

        return changes;
    }

    @VisibleForTesting
    static class DependencyChanges {
        List<SonarResolvedDependency> missingDependencies = new ArrayList<>();
        Map<SonarResolvedDependencyId, VersionChange> changedVersions = new LinkedHashMap<>();
        List<SonarResolvedDependency> unexpectedDependencies = new ArrayList<>();
    }

    @Value
    @EqualsAndHashCode(cacheStrategy = LAZY)
    @VisibleForTesting
    static class SonarResolvedDependencyId {
        String moduleGroup;
        String moduleName;

        @Override
        public String toString() {
            return moduleGroup + ":" + moduleName;
        }
    }

    @Value
    @VisibleForTesting
    static class VersionChange {
        String expectedVersion;
        String actualVersion;
    }


    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

}
