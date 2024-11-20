package name.remal.gradle_plugins.sonarlint;

import static com.google.common.util.concurrent.Callables.returning;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_JAVA_BINARIES;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_JAVA_ENABLE_PREVIEW_PROPERTY;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_JAVA_LIBRARIES;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_JAVA_SOURCE_PROPERTY;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_JAVA_TARGET_PROPERTY;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_JAVA_TEST_BINARIES;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_JAVA_TEST_LIBRARIES;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_LIST_PROPERTY_DELIMITER;
import static name.remal.gradle_plugins.sonarlint.BaseSonarLintActions.SONAR_SOURCE_ENCODING;
import static name.remal.gradle_plugins.sonarlint.DependencyWithBrokenPomSubstitutions.getVersionWithFixedPom;
import static name.remal.gradle_plugins.sonarlint.ResolvedNonReproducibleSonarDependencies.getResolvedNonReproducibleSonarDependency;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.getSonarDependencies;
import static name.remal.gradle_plugins.toolkit.ExtensionContainerUtils.findExtension;
import static name.remal.gradle_plugins.toolkit.ExtensionContainerUtils.getExtension;
import static name.remal.gradle_plugins.toolkit.PredicateUtils.not;
import static name.remal.gradle_plugins.toolkit.PropertiesConventionUtils.setPropertyConvention;
import static name.remal.gradle_plugins.toolkit.ResolutionStrategyUtils.configureGlobalResolutionStrategy;
import static name.remal.gradle_plugins.toolkit.SourceSetUtils.isSourceSetTask;
import static name.remal.gradle_plugins.toolkit.SourceSetUtils.whenTestSourceSetRegistered;
import static org.gradle.api.artifacts.ExcludeRule.GROUP_KEY;
import static org.gradle.api.artifacts.ExcludeRule.MODULE_KEY;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.Usage.JAVA_RUNTIME;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

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
import javax.inject.Inject;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Value;
import lombok.val;
import name.remal.gradle_plugins.toolkit.FileUtils;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.annotations.ReliesOnInternalGradleApi;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CodeQualityExtension;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
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

    @SuppressWarnings({"HidingField", "java:S2387"})
    protected Project project;

    @Override
    protected void beforeApply() {
        this.project = super.project;
    }


    @Override
    protected String getToolName() {
        return "SonarLint";
    }

    @Override
    protected Class<SonarLint> getTaskType() {
        return SonarLint.class;
    }

    @Override
    protected String getConfigurationName() {
        return super.getConfigurationName() + "Core";
    }

    protected String getPluginsConfigurationName() {
        return getConfigurationName() + "Plugins";
    }

    protected String getClasspathConfigurationName() {
        return getConfigurationName() + "Classpath";
    }

    @Override
    protected void configureConfiguration(Configuration configuration) {
        configureSonarLintConfiguration(configuration);

        getSonarDependencies().values().stream()
            .filter(sonarDependency -> sonarDependency.getType() == SonarDependencyType.CORE)
            .forEach(sonarDependency -> {
                configuration.getDependencies().add(
                    createDependency(sonarDependency)
                );
            });
    }

    @Override
    @SuppressWarnings("Slf4jFormatShouldBeConst")
    protected void createConfigurations() {
        configureGlobalResolutionStrategy(project, resolutionStrategy -> {
            try {
                resolutionStrategy.eachDependency(details -> {
                    val target = details.getTarget();
                    val targetNotation = target.getGroup() + ':' + target.getName() + ':' + target.getVersion();
                    val fixedVersion = getVersionWithFixedPom(targetNotation);
                    if (fixedVersion != null) {
                        details.because("Fix dependency with broken POM")
                            .useVersion(fixedVersion);
                    }
                });

            } catch (Throwable e) {
                logger.trace(e.toString(), e);
            }
        });

        super.createConfigurations();

        project.getConfigurations().create(getPluginsConfigurationName(), conf -> {
            if (conf.isCanBeResolved()) {
                conf.setCanBeResolved(false);
            }

            configureSonarLintConfiguration(conf);
            conf.setDescription(getToolName() + " plugins to be used for this project.");

            conf.getDependencies().withType(ModuleDependency.class).configureEach(dep -> {
                dep.setTransitive(false);
            });

            getSonarDependencies().values().stream()
                .filter(sonarDependency -> sonarDependency.getType() == SonarDependencyType.PLUGIN)
                .forEach(sonarDependency -> {
                    conf.getDependencies().add(
                        createDependency(sonarDependency)
                    );
                });
        });

        project.getConfigurations().create(getClasspathConfigurationName(), conf -> {
            if (!conf.isCanBeResolved()) {
                conf.setCanBeResolved(true);
            }

            configureSonarLintConfiguration(conf);
            conf.setDescription("Full " + getToolName() + " classpath to be used for this project.");

            conf.extendsFrom(
                project.getConfigurations().getByName(getConfigurationName()),
                project.getConfigurations().getByName(getPluginsConfigurationName())
            );
        });
    }

    @Override
    protected CodeQualityExtension createExtension() {
        val extension = project.getExtensions().create("sonarLint", SonarLintExtension.class);
        this.extension = extension;

        Collection<SourceSet> testSourceSets = new ArrayList<>();
        whenTestSourceSetRegistered(project, testSourceSets::add);
        setPropertyConvention(extension, "testSourceSets", returning(testSourceSets));


        project.getTasks().withType(BaseSonarLint.class)
            .configureEach(this::configureTaskDefaults);

        project.getTasks().register("sonarLintProperties", SonarLintPropertiesHelp.class);
        project.getTasks().register("sonarLintRules", SonarLintRulesHelp.class);

        return extension;
    }

    private void configureTaskDefaults(BaseSonarLint task) {
        if (task instanceof VerificationTask) {
            setPropertyConvention(task, "ignoreFailures", extension::isIgnoreFailures);
        }

        task.getToolClasspath().setFrom(
            project.getConfigurations().getByName(getClasspathConfigurationName())
        );

        val extension = (SonarLintExtension) this.extension;
        task.getNodeJs().set(extension.getNodeJs());
        task.getCheckstyleConfig().convention(project.getLayout().file(getProviders().provider(
            this::getCheckstyleConfigFile
        )));
        task.getDisableRulesConflictingWithLombok().convention(getProviders().provider(() ->
            TRUE.equals(extension.getRules().getDisableConflictingWithLombok().getOrNull())
        ));
        task.getLoggingOptions().set(extension.getLogging());
        task.getForkOptions().set(extension.getFork());
    }

    @Override
    protected void configureTaskDefaults(SonarLint task, String baseName) {
        // do nothing
    }

    @Override
    @ReliesOnInternalGradleApi
    @SuppressWarnings("java:S3776")
    protected void configureForSourceSet(SourceSet sourceSet, SonarLint task) {
        task.setDescription("Run " + getToolName() + " analysis for " + sourceSet.getName() + " classes");
        task.dependsOn(sourceSet.getClassesTaskName());

        task.dependsOn(sourceSet.getAllSource());
        task.setSource(sourceSet.getAllSource());

        val sourceSetCompileTasks = project.getTasks().withType(AbstractCompile.class)
            .matching(compileTask -> isSourceSetTask(sourceSet, compileTask));
        task.dependsOn(sourceSetCompileTasks);
        task.source(getProviders().provider(() -> {
            //noinspection ConstantConditions
            return sourceSetCompileTasks.stream()
                .filter(HasCompileOptions.class::isInstance)
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

        task.getIsTest().convention(getProviders().provider(() ->
            getTestSourceSets().contains(sourceSet)
        ));

        task.dependsOn(getProviders().provider(() -> {
            val testSourceSets = getTestSourceSets();
            val mainSourceSets = getExtension(project, SourceSetContainer.class).stream()
                .filter(not(testSourceSets::contains))
                .collect(toList());
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

        val lazyOutputDirsAndLibraries = getObjects().property(OutputDirsAndLibraries.class)
            .value(getProviders().provider(() -> {
                val testSourceSets = getTestSourceSets();
                val mainSourceSets = getExtension(project, SourceSetContainer.class).stream()
                    .filter(not(testSourceSets::contains))
                    .collect(toList());
                final Collection<File> mainOutputDirs;
                final Collection<File> mainLibraries;
                final Collection<File> testOutputDirs;
                final Collection<File> testLibraries;
                if (mainSourceSets.contains(sourceSet)) {
                    mainOutputDirs = getOutputDirs(singletonList(sourceSet));
                    mainLibraries = getLibraries(singletonList(sourceSet));

                    testOutputDirs = getOutputDirs(testSourceSets);
                    testLibraries = getLibraries(testSourceSets);

                } else if (testSourceSets.contains(sourceSet)) {
                    mainOutputDirs = getOutputDirs(mainSourceSets);
                    mainLibraries = getLibraries(mainSourceSets);

                    testOutputDirs = getOutputDirs(singletonList(sourceSet));
                    testLibraries = getLibraries(singletonList(sourceSet));

                } else {
                    mainOutputDirs = getOutputDirs(singletonList(sourceSet));
                    mainLibraries = getLibraries(singletonList(sourceSet));

                    testOutputDirs = mainOutputDirs;
                    testLibraries = mainLibraries;
                }

                return OutputDirsAndLibraries.builder()
                    .mainOutputDirs(mainOutputDirs)
                    .mainLibraries(mainLibraries)
                    .testOutputDirs(testOutputDirs)
                    .testLibraries(testLibraries)
                    .build();
            }));
        lazyOutputDirsAndLibraries.finalizeValueOnRead();

        task.onlyIf(__ -> {
            val outputDirsAndLibraries = lazyOutputDirsAndLibraries.get();
            Stream.of(
                    outputDirsAndLibraries.getMainOutputDirs(),
                    outputDirsAndLibraries.getMainLibraries(),
                    outputDirsAndLibraries.getTestOutputDirs(),
                    outputDirsAndLibraries.getTestLibraries()
                )
                .flatMap(Collection::stream)
                .distinct()
                .forEach(file -> {
                    if (file.isDirectory()) {
                        task.getInputs().dir(file)
                            .ignoreEmptyDirectories()
                            .optional();
                    } else {
                        task.getInputs().file(file)
                            .ignoreEmptyDirectories()
                            .optional();
                    }
                });

            return true;
        });

        task.getSonarProperties().putAll(getProviders().provider(() -> {
            Map<String, String> javaProps = new LinkedHashMap<>();

            val javaCompileTask = project.getTasks()
                .withType(JavaCompile.class)
                .stream()
                .filter(it -> it.getName().equals(sourceSet.getCompileJavaTaskName()))
                .findFirst();
            javaCompileTask.map(JavaCompile::getOptions)
                .map(CompileOptions::getEncoding)
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(encoding ->
                    javaProps.put(SONAR_SOURCE_ENCODING, encoding)
                );
            javaCompileTask.map(JavaCompile::getSourceCompatibility)
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(sourceCompatibility ->
                    javaProps.put(SONAR_JAVA_SOURCE_PROPERTY, sourceCompatibility)
                );
            javaCompileTask.map(JavaCompile::getTargetCompatibility)
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(targetCompatibility ->
                    javaProps.put(SONAR_JAVA_TARGET_PROPERTY, targetCompatibility)
                );
            javaCompileTask.map(JavaCompile::getOptions)
                .map(CompileOptions::getCompilerArgs)
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(args -> {
                    val isPreviewEnabled = args.stream().anyMatch("--enable-preview"::equals);
                    if (isPreviewEnabled) {
                        javaProps.put(SONAR_JAVA_ENABLE_PREVIEW_PROPERTY, "true");
                    }
                });

            val outputDirsAndLibraries = lazyOutputDirsAndLibraries.get();
            javaProps.put(SONAR_JAVA_BINARIES, outputDirsAndLibraries.getMainOutputDirs().stream()
                .map(File::getPath)
                .collect(joining(SONAR_LIST_PROPERTY_DELIMITER))
            );
            javaProps.put(SONAR_JAVA_LIBRARIES, outputDirsAndLibraries.getMainLibraries().stream()
                .map(File::getPath)
                .collect(joining(SONAR_LIST_PROPERTY_DELIMITER))
            );
            javaProps.put(SONAR_JAVA_TEST_BINARIES, outputDirsAndLibraries.getTestOutputDirs().stream()
                .map(File::getPath)
                .collect(joining(SONAR_LIST_PROPERTY_DELIMITER))
            );
            javaProps.put(SONAR_JAVA_TEST_LIBRARIES, outputDirsAndLibraries.getTestLibraries().stream()
                .map(File::getPath)
                .collect(joining(SONAR_LIST_PROPERTY_DELIMITER))
            );

            return javaProps;
        }));

        task.getCheckstyleConfig().convention(project.getLayout().file(getProviders().provider(() ->
            getCheckstyleConfigFile(sourceSet)
        )));

        task.getDisableRulesConflictingWithLombok().convention(getProviders().provider(() ->
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

    @Value
    @Builder
    private static class OutputDirsAndLibraries {
        Collection<File> mainOutputDirs;
        Collection<File> mainLibraries;
        Collection<File> testOutputDirs;
        Collection<File> testLibraries;
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
            .map(FileUtils::normalizeFile)
            .distinct()
            .collect(toList());
    }

    private static Collection<File> getLibraries(Iterable<? extends SourceSet> sourceSets) {
        return StreamSupport.stream(sourceSets.spliterator(), false)
            .map(SourceSet::getCompileClasspath)
            .flatMap(files -> StreamSupport.stream(files.spliterator(), false))
            .map(FileUtils::normalizeFile)
            .distinct()
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


        if (configuration.isCanBeResolved() || configuration.isCanBeConsumed()) {
            configuration.attributes(attrs -> {
                attrs.attribute(
                    USAGE_ATTRIBUTE,
                    project.getObjects().named(Usage.class, JAVA_RUNTIME)
                );
                attrs.attribute(
                    CATEGORY_ATTRIBUTE,
                    project.getObjects().named(Category.class, LIBRARY)
                );
            });
        }


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


        configuration.getResolutionStrategy().eachDependency(details -> {
            val target = details.getTarget();
            val notation = target.getGroup() + ':' + target.getName() + ':' + target.getVersion();
            val nonReproducibleDependency = getResolvedNonReproducibleSonarDependency(notation);
            if (nonReproducibleDependency != null) {
                details.because("Replace non-reproducible version with predefined one")
                    .useVersion(nonReproducibleDependency.getVersion());
            }
        });
    }

    private Dependency createDependency(SonarDependency sonarDependency) {
        return getDependencies().create(format(
            "%s:%s:%s",
            sonarDependency.getGroup(),
            sonarDependency.getName(),
            sonarDependency.getVersion()
        ));
    }


    @Inject
    protected abstract DependencyHandler getDependencies();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

}
