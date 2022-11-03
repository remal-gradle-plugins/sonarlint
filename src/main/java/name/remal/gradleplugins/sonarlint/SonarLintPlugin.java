package name.remal.gradleplugins.sonarlint;

import static com.google.common.util.concurrent.Callables.returning;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static name.remal.gradleplugins.sonarlint.BuildInfo.PLUGIN_GROUP_ID;
import static name.remal.gradleplugins.sonarlint.BuildInfo.PLUGIN_VERSION;
import static name.remal.gradleplugins.sonarlint.CanonizationUtils.canonizeProperties;
import static name.remal.gradleplugins.sonarlint.CanonizationUtils.canonizeRules;
import static name.remal.gradleplugins.sonarlint.CanonizationUtils.canonizeRulesProperties;
import static name.remal.gradleplugins.sonarlint.SonarDependencies.getSonarDependencies;
import static name.remal.gradleplugins.sonarlint.SonarDependencies.getSonarDependency;
import static name.remal.gradleplugins.toolkit.ExtensionContainerUtils.findExtension;
import static name.remal.gradleplugins.toolkit.ExtensionContainerUtils.getExtension;
import static name.remal.gradleplugins.toolkit.ObjectUtils.defaultTrue;
import static name.remal.gradleplugins.toolkit.PredicateUtils.not;
import static name.remal.gradleplugins.toolkit.PropertiesConventionUtils.setPropertyConvention;
import static name.remal.gradleplugins.toolkit.SourceSetUtils.isSourceSetTask;
import static name.remal.gradleplugins.toolkit.SourceSetUtils.whenTestSourceSetRegistered;
import static org.gradle.api.artifacts.ExcludeRule.GROUP_KEY;
import static org.gradle.api.artifacts.ExcludeRule.MODULE_KEY;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.val;
import name.remal.gradleplugins.toolkit.ObjectUtils;
import name.remal.gradleplugins.toolkit.ReliesOnInternalGradleApi;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CodeQualityExtension;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.Unmodifiable;

@CustomLog
@ReliesOnInternalGradleApi
public abstract class SonarLintPlugin extends AbstractCodeQualityPlugin<SonarLint> {

    @Override
    protected String getToolName() {
        return "SonarLint";
    }

    @Override
    protected Class<SonarLint> getTaskType() {
        return SonarLint.class;
    }

    protected String getRunnerConfigurationName() {
        return getConfigurationName() + "Runner";
    }

    protected String getPluginsConfigurationName() {
        return getConfigurationName() + "Plugins";
    }

    protected String getClasspathConfigurationName() {
        return getConfigurationName() + "ToolClasspath";
    }

    @Override
    protected void configureConfiguration(Configuration configuration) {
        configureSonarLintConfiguration(configuration);

        configuration.defaultDependencies(dependencies -> {
            dependencies.add(createDependency(
                getSonarDependency("sonarlint-core"),
                extension.getToolVersion()
            ));
        });
    }

    @Override
    protected void createConfigurations() {
        super.createConfigurations();

        {
            val runnerConfiguration = project.getConfigurations().create(getRunnerConfigurationName());
            runnerConfiguration.setDescription("The " + getToolName() + " runner to be used for this project.");
            configureSonarLintConfiguration(runnerConfiguration);

            runnerConfiguration.defaultDependencies(dependencies -> {
                dependencies.add(
                    project.getDependencies().create(ImmutableMap.of(
                        "group", PLUGIN_GROUP_ID,
                        "name", "runner",
                        "version", PLUGIN_VERSION
                    ))
                );
            });
        }

        {
            val pluginsConfiguration = project.getConfigurations().create(getPluginsConfigurationName());
            pluginsConfiguration.setDescription(getToolName() + " plugins to be used for this project.");
            configureSonarLintConfiguration(pluginsConfiguration);

            getSonarDependencies().values().stream()
                .filter(sonarDependency -> sonarDependency.getType() == SonarDependencyType.PLUGIN)
                .forEach(sonarDependency -> {
                    pluginsConfiguration.getDependencies().add(
                        createDependency(sonarDependency)
                    );
                });
        }

        {
            val classpathConfiguration = project.getConfigurations().create(getClasspathConfigurationName());
            classpathConfiguration.setDescription("Full " + getToolName() + " classpath to be used for this project.");
            configureSonarLintConfiguration(classpathConfiguration);

            classpathConfiguration.extendsFrom(
                project.getConfigurations().getByName(getRunnerConfigurationName()),
                project.getConfigurations().getByName(getConfigurationName()),
                project.getConfigurations().getByName(getPluginsConfigurationName())
            );
        }
    }

    @Override
    protected CodeQualityExtension createExtension() {
        val extension = project.getExtensions().create("sonarLint", SonarLintExtension.class, project);
        this.extension = extension;

        extension.setToolVersion(getSonarDependency("sonarlint-core").getVersion());

        Collection<SourceSet> testSourceSets = new ArrayList<>();
        whenTestSourceSetRegistered(project, testSourceSets::add);
        setPropertyConvention(extension, "testSourceSets", returning(testSourceSets));


        project.getTasks().withType(BaseSonarLint.class)
            .configureEach(this::configureTaskDefaults);

        project.getTasks().register("sonarLintProperties", SonarLintPropertiesHelp.class);
        project.getTasks().register("sonarLintRules", SonarLintRulesHelp.class);

        return extension;
    }

    protected void configureTaskDefaults(BaseSonarLint task) {
        if (task instanceof VerificationTask) {
            setPropertyConvention(task, "ignoreFailures", extension::isIgnoreFailures);
        }

        task.getToolClasspath().setFrom(
            project.getConfigurations().getByName(getClasspathConfigurationName())
        );

        val extension = (SonarLintExtension) this.extension;
        task.getIsGeneratedCodeIgnored().convention(project.provider(() ->
            defaultTrue(extension.getIsGeneratedCodeIgnored().getOrNull())
        ));
        task.getEnabledRules().convention(project.provider(() ->
            canonizeRules(extension.getRules().getEnabled())
        ));
        task.getDisabledRules().convention(project.provider(() ->
            canonizeRules(extension.getRules().getDisabled())
        ));
        task.getSonarProperties().convention(project.provider(() ->
            canonizeProperties(extension.getSonarProperties())
        ));
        task.getRulesProperties().convention(project.provider(() ->
            canonizeRulesProperties(extension.getRules().getProperties())
        ));
        task.getCheckstyleConfig().convention(project.getLayout().file(project.provider(
            this::getCheckstyleConfigFile
        )));
        task.getDisableRulesConflictingWithLombok().convention(project.provider(() ->
            TRUE.equals(extension.getRules().getDisableConflictingWithLombok())
        ));
    }

    @Override
    protected void configureTaskDefaults(SonarLint task, String baseName) {
        // do nothing
    }

    @Override
    @ReliesOnInternalGradleApi
    protected void configureForSourceSet(SourceSet sourceSet, SonarLint task) {
        task.setDescription("Run " + getToolName() + " analysis for " + sourceSet.getName() + " classes");
        task.dependsOn(sourceSet.getClassesTaskName());

        task.setSource(sourceSet.getAllSource());
        task.source(project.provider(() -> {
            //noinspection ConstantConditions
            return project.getTasks().withType(AbstractCompile.class).stream()
                .filter(HasCompileOptions.class::isInstance)
                .filter(compileTask -> isSourceSetTask(sourceSet, compileTask))
                .map(HasCompileOptions.class::cast)
                .map(compileTask -> Optional.ofNullable(compileTask.getOptions())
                    .map(CompileOptions::getGeneratedSourceOutputDirectory)
                    .map(DirectoryProperty::getAsFile)
                    .map(Provider::getOrNull)
                    .map(File::getAbsoluteFile)
                    .orElse(null)
                )
                .filter(Objects::nonNull)
                .collect(toList());
        }));

        task.getIsTest().convention(project.provider(() ->
            getTestSourceSets().contains(sourceSet)
        ));

        val testSourceSets = getTestSourceSets();
        val mainSourceSets = getExtension(project, SourceSetContainer.class).stream()
            .filter(not(testSourceSets::contains))
            .collect(toList());
        task.dependsOn(project.provider(() -> {
            if (mainSourceSets.contains(sourceSet)) {
                return testSourceSets.stream()
                    .map(SourceSet::getClassesTaskName)
                    .collect(toList());
            } else if (testSourceSets.contains(sourceSet)) {
                return mainSourceSets.stream()
                    .map(SourceSet::getClassesTaskName)
                    .collect(toList());
            } else {
                return emptyList();
            }
        }));

        task.getSonarProperties().putAll(project.provider(() -> {
            Map<String, String> javaProps = new LinkedHashMap<>();

            val javaCompileTask = project.getTasks()
                .withType(JavaCompile.class)
                .stream()
                .filter(it -> it.getName().equals(sourceSet.getCompileJavaTaskName()))
                .findFirst();
            javaCompileTask.map(JavaCompile::getSourceCompatibility)
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(sourceCompatibility ->
                    javaProps.put("sonar.java.source", sourceCompatibility)
                );
            javaCompileTask.map(JavaCompile::getTargetCompatibility)
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(targetCompatibility ->
                    javaProps.put("sonar.java.target", targetCompatibility)
                );


            final Collection<File> mainOutputDirs;
            final Collection<File> mainLibraries;
            final Collection<File> testOutputDirs;
            final Collection<File> testLibraries;
            if (mainSourceSets.contains(sourceSet)) {
                mainOutputDirs = getOutputDirs(singletonList(sourceSet));
                mainLibraries = getLibraries(singletonList(sourceSet));

                testOutputDirs = getOutputDirs(testSourceSets);
                testLibraries = getLibraries(testSourceSets).stream()
                    .filter(not(mainOutputDirs::contains))
                    .filter(not(mainLibraries::contains))
                    .collect(toList());

            } else if (testSourceSets.contains(sourceSet)) {
                mainOutputDirs = getOutputDirs(mainSourceSets);
                mainLibraries = getLibraries(mainSourceSets);

                testOutputDirs = getOutputDirs(singletonList(sourceSet));
                testLibraries = getLibraries(singletonList(sourceSet)).stream()
                    .filter(not(mainOutputDirs::contains))
                    .filter(not(mainLibraries::contains))
                    .collect(toList());

            } else {
                mainOutputDirs = getOutputDirs(singletonList(sourceSet));
                mainLibraries = getLibraries(singletonList(sourceSet));

                testOutputDirs = mainOutputDirs;
                testLibraries = mainLibraries;
            }

            javaProps.put("sonar.java.binaries", mainOutputDirs.stream()
                .map(File::getPath)
                .collect(joining(","))
            );
            javaProps.put("sonar.java.libraries", mainLibraries.stream()
                .map(File::getPath)
                .collect(joining(","))
            );
            javaProps.put("sonar.java.test.binaries", testOutputDirs.stream()
                .map(File::getPath)
                .collect(joining(","))
            );
            javaProps.put("sonar.java.test.libraries", testLibraries.stream()
                .map(File::getPath)
                .collect(joining(","))
            );

            return javaProps;
        }));

        task.getCheckstyleConfig().convention(project.getLayout().file(project.provider(() ->
            getCheckstyleConfigFile(sourceSet)
        )));

        task.getDisableRulesConflictingWithLombok().convention(project.provider(() ->
            project.getConfigurations()
                .getByName(sourceSet.getCompileClasspathConfigurationName())
                .getResolvedConfiguration()
                .getLenientConfiguration()
                .getAllModuleDependencies()
                .stream()
                .anyMatch(dep ->
                    Objects.equals(dep.getModuleGroup(), "org.projectlombok")
                        && Objects.equals(dep.getModuleName(), "lombok")
                )
        ));
    }

    @Unmodifiable
    private Collection<SourceSet> getTestSourceSets() {
        val extension = (SonarLintExtension) this.extension;
        val testSourceSets = extension.getTestSourceSets();
        return testSourceSets != null
            ? unmodifiableCollection(new LinkedHashSet<>(testSourceSets))
            : emptyList();
    }

    private static Collection<File> getOutputDirs(Iterable<? extends SourceSet> sourceSets) {
        return StreamSupport.stream(sourceSets.spliterator(), false)
            .map(SourceSet::getOutput)
            .flatMap(output ->
                Stream.of(
                    output.getClassesDirs(),
                    singletonList(output.getResourcesDir()),
                    output.getDirs()
                ).flatMap(files -> StreamSupport.stream(files.spliterator(), false))
            )
            .map(File::getAbsoluteFile)
            .distinct()
            .filter(File::exists)
            .collect(toList());
    }

    private static Collection<File> getLibraries(Iterable<? extends SourceSet> sourceSets) {
        return StreamSupport.stream(sourceSets.spliterator(), false)
            .map(SourceSet::getCompileClasspath)
            .flatMap(files -> StreamSupport.stream(files.spliterator(), false))
            .map(File::getAbsoluteFile)
            .distinct()
            .filter(File::exists)
            .collect(toList());
    }

    @Nullable
    @SuppressWarnings("ConstantConditions")
    private File getCheckstyleConfigFile(SourceSet sourceSet) {
        return project.getTasks().stream()
            .filter(Checkstyle.class::isInstance)
            .map(Checkstyle.class::cast)
            .filter(it -> it.getName().equals(sourceSet.getTaskName("checkstyle", null)))
            .map(Checkstyle::getConfigFile)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(this::getCheckstyleConfigFile);
    }

    @Nullable
    private File getCheckstyleConfigFile() {
        return Optional.ofNullable(findExtension(project, CheckstyleExtension.class))
            .map(CheckstyleExtension::getConfigFile)
            .orElse(null);
    }


    private void configureSonarLintConfiguration(Configuration configuration) {
        configuration.setVisible(false);
        configuration.setCanBeConsumed(false);

        configuration.exclude(ImmutableMap.of(
            GROUP_KEY, "ch.qos.logback",
            MODULE_KEY, "*"
        ));
        configuration.exclude(ImmutableMap.of(
            GROUP_KEY, "org.springframework",
            MODULE_KEY, "spring-jcl"
        ));

        if (!configuration.getName().equals(getConfigurationName())) {
            project.getConfigurations()
                .getByName(getConfigurationName())
                .getExcludeRules()
                .forEach(excludeRule -> {
                    configuration.exclude(ImmutableMap.of(
                        GROUP_KEY, excludeRule.getGroup(),
                        MODULE_KEY, excludeRule.getModule()
                    ));
                });
        }
    }

    private Dependency createDependency(SonarDependency sonarDependency) {
        return project.getDependencies().create(format(
            "%s:%s:%s",
            sonarDependency.getGroup(),
            sonarDependency.getName(),
            sonarDependency.getVersion()
        ));
    }

    private Dependency createDependency(SonarDependency sonarDependency, String version) {
        return project.getDependencies().create(format(
            "%s:%s:%s",
            sonarDependency.getGroup(),
            sonarDependency.getName(),
            version
        ));
    }

}
