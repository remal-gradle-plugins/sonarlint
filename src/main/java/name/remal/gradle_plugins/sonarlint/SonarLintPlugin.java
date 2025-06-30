package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static name.remal.gradle_plugins.sonarlint.DependencyWithBrokenVersion.areFixedVersionForBrokenDependenciesRegistered;
import static name.remal.gradle_plugins.sonarlint.DependencyWithBrokenVersion.getFixedVersionForBrokenDependency;
import static name.remal.gradle_plugins.sonarlint.ResolvedNonReproducibleSonarDependencies.areResolvedNonReproducibleSonarDependenciesRegistered;
import static name.remal.gradle_plugins.sonarlint.ResolvedNonReproducibleSonarDependencies.getResolvedNonReproducibleSonarDependency;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.SONARLINT_CORE_DEPENDENCIES;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.SONARLINT_CORE_IMPL_LOGGING_EXCLUSIONS;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.SONARLINT_CORE_LIBRARIES_EXCLUSIONS;
import static name.remal.gradle_plugins.sonarlint.SonarJavascriptPluginInfo.SONAR_JAVASCRIPT_PLUGIN_DEPENDENCY;
import static name.remal.gradle_plugins.toolkit.ActionUtils.doNothingAction;
import static name.remal.gradle_plugins.toolkit.AttributeContainerUtils.javaRuntimeLibrary;
import static name.remal.gradle_plugins.toolkit.GradleManagedObjectsUtils.copyManagedProperties;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.doNotInline;
import static name.remal.gradle_plugins.toolkit.SourceSetUtils.isSourceSetTask;
import static name.remal.gradle_plugins.toolkit.reflection.MembersFinder.findMethod;

import com.tisonkun.os.core.Arch;
import com.tisonkun.os.core.OS;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.CustomLog;
import name.remal.gradle_plugins.sonarlint.SonarJavascriptPluginInfo.EmbeddedNodeJsPlatform;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.reflection.TypedVoidMethod1;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

@CustomLog
public abstract class SonarLintPlugin implements Plugin<Project> {

    public static final String SONARLINT_EXTENSION_NAME = doNotInline("sonarLint");

    @Override
    public void apply(Project project) {
        var extension = project.getExtensions().create(SONARLINT_EXTENSION_NAME, SonarLintExtension.class);


        var buildServices = project.getGradle().getSharedServices();

        var buildData = buildServices.registerIfAbsent(
            getBuildServiceName(SonarLintBuildData.class),
            SonarLintBuildData.class,
            doNothingAction()
        ).get();
        buildData.registerProjectLanguagesSettings(project, extension.getLanguages());

        var buildServiceName = getBuildServiceName(SonarLintBuildService.class);
        if (!buildServices.getRegistrations().getNames().contains(buildServiceName)) {
            var coreConf = createSonarLintConfiguration(conf -> {
                conf.setDescription("SonarLint core");

                conf.defaultDependencies(deps -> {
                    SONARLINT_CORE_DEPENDENCIES.stream()
                        .map(this::processSonarDependency)
                        .map(SonarDependency::getNotation)
                        .map(getDependencies()::create)
                        .forEach(conf.getDependencies()::add);
                });
            });

            var loggingConf = createSonarLintConfiguration(conf -> {
                conf.setDescription("SonarLint logging impl");

                conf.defaultDependencies(deps -> {
                    var allModuleDependencies = coreConf
                        .getResolvedConfiguration()
                        .getLenientConfiguration()
                        .getAllModuleDependencies();

                    var slf4jVersion = allModuleDependencies.stream()
                        .filter(dep ->
                            "org.slf4j".equals(dep.getModuleGroup())
                                && "slf4j-api".equals(dep.getModuleName())
                        )
                        .map(ResolvedDependency::getModuleVersion)
                        .findAny()
                        .orElseThrow(() -> new IllegalStateException("SLF4J version can't be determined"));

                    var moduleNames = new ArrayList<String>();
                    moduleNames.add("slf4j-simple");

                    moduleNames.stream()
                        .map(name -> format("org.slf4j:%s:%s", name, slf4jVersion))
                        .map(getDependencies()::create)
                        .forEach(conf.getDependencies()::add);
                });
            });

            var pluginsConf = createSonarLintConfiguration(conf -> {
                conf.setDescription("SonarLint plugins");
                conf.getDependencies().withType(ModuleDependency.class).configureEach(dep -> dep.setTransitive(false));

                conf.defaultDependencies(deps -> {
                    buildData.getLanguagesToProcess().stream()
                        .map(SonarLintLanguage::getPluginDependencies)
                        .flatMap(Collection::stream)
                        .distinct()
                        .sorted()
                        .map(this::processSonarDependency)
                        .map(SonarDependency::getNotation)
                        .map(getDependencies()::create)
                        .forEach(deps::add);
                });
            });

            buildServices.registerIfAbsent(
                buildServiceName,
                SonarLintBuildService.class,
                buildServiceSpec -> {
                    var buildServiceParams = buildServiceSpec.getParameters();
                    buildServiceParams.getCoreClasspath().from(coreConf);
                    buildServiceParams.getLoggingClasspath().from(loggingConf);
                    buildServiceParams.getPluginFiles().from(pluginsConf);
                }
            );
        }


        configureAllSonarLintTasks(project, extension, null, null);

        project.getPluginManager().withPlugin("java", __ -> configureJvmProject(project, extension));

        project.getTasks().register("sonarLintProperties", SonarLintHelpProperties.class);
        project.getTasks().register("sonarLintRules", SonarLintHelpRules.class);
    }

    private String getBuildServiceName(Class<? extends BuildService<?>> buildServiceClass) {
        return buildServiceClass.getName()
            + '/' + buildServiceClass.hashCode()
            + '/' + buildServiceClass.getClassLoader().hashCode()
            + '/' + SonarLintPlugin.class.hashCode()
            + '/' + SonarLintPlugin.class.getClassLoader().hashCode()
            ;
    }


    private Configuration createSonarLintConfiguration(Action<Configuration> configurer) {
        var configuration = getConfigurations().detachedConfiguration();

        configuration.setCanBeResolved(true);
        configuration.setVisible(false);

        if (configuration.isCanBeResolved() || configuration.isCanBeConsumed()) {
            configuration.attributes(javaRuntimeLibrary(getObjects()));
        }

        SONARLINT_CORE_LIBRARIES_EXCLUSIONS.forEach(configuration::exclude);
        SONARLINT_CORE_IMPL_LOGGING_EXCLUSIONS.forEach(configuration::exclude);
        //SONARLINT_CORE_ALL_LOGGING_EXCLUSIONS.forEach(configuration::exclude);

        if (areFixedVersionForBrokenDependenciesRegistered()) {
            configuration.getResolutionStrategy().eachDependency(details -> {
                var target = details.getTarget();
                var targetNotation = target.getGroup() + ':' + target.getName() + ':' + target.getVersion();
                var fixedVersion = getFixedVersionForBrokenDependency(targetNotation);
                if (fixedVersion != null) {
                    details.because("Fix dependency with broken version")
                        .useVersion(fixedVersion);
                }
            });
        }

        if (areResolvedNonReproducibleSonarDependenciesRegistered()) {
            configuration.getResolutionStrategy().eachDependency(details -> {
                var target = details.getTarget();
                var notation = target.getGroup() + ':' + target.getName() + ':' + target.getVersion();
                var nonReproducibleDependency = getResolvedNonReproducibleSonarDependency(notation);
                if (nonReproducibleDependency != null) {
                    details.because("Replace non-reproducible version with predefined one")
                        .useVersion(nonReproducibleDependency.getVersion());
                }
            });
        }

        configurer.execute(configuration);

        return configuration;
    }

    private SonarDependency processSonarDependency(SonarDependency dependency) {
        if (dependency.getId().equals(SONAR_JAVASCRIPT_PLUGIN_DEPENDENCY_ID)) {
            var platform = getEmbeddedNodeJsPlatform();
            if (platform != null) {
                var newClassifier = platform.getJavascriptPluginClassifier();
                return dependency.withClassifier(newClassifier);
            }
        }

        return dependency;
    }

    private static final String SONAR_JAVASCRIPT_PLUGIN_DEPENDENCY_ID = SONAR_JAVASCRIPT_PLUGIN_DEPENDENCY.getId();

    @Nullable
    private EmbeddedNodeJsPlatform getEmbeddedNodeJsPlatform() {
        var osDetector = this.osDetector.get();
        var detected = osDetector.getDetectedOs();
        if (detected.os == OS.windows) {
            if (detected.arch == Arch.x86_64) {
                return EmbeddedNodeJsPlatform.WIN_X64;
            }

        } else if (detected.os == OS.linux) {
            if (detected.arch == Arch.aarch_64) {
                return EmbeddedNodeJsPlatform.LINUX_ARM64;
            } else if (detected.arch == Arch.x86_64) {
                return osDetector.isAlpine()
                    ? EmbeddedNodeJsPlatform.LINUX_X64_MUSL
                    : EmbeddedNodeJsPlatform.LINUX_X64;
            }

        } else if (detected.os == OS.osx) {
            if (detected.arch == Arch.aarch_64) {
                return EmbeddedNodeJsPlatform.DARWIN_ARM64;
            } else if (detected.arch == Arch.x86_64) {
                return EmbeddedNodeJsPlatform.DARWIN_X64;
            }
        }

        return null;
    }

    private final LazyValue<OsDetector> osDetector = lazyValue(() -> getObjects().newInstance(OsDetector.class));


    private void configureAllSonarLintTasks(
        Project project,
        SonarLintExtension extension,
        Provider<Configuration> coreConf,
        Provider<Configuration> pluginsConf
    ) {
        project.getTasks().withType(AbstractSonarLintTask.class).configureEach(task -> {
            copyManagedProperties(SonarLintSettings.class, extension, task.getSettings());
            copyManagedProperties(SonarLintLanguagesSettings.class, extension.getLanguages(), task.getLanguages());
            task.getCoreClasspath().from(coreConf);
            task.getPluginFiles().from(pluginsConf);
        });
    }


    @SuppressWarnings("java:S3776")
    private void configureJvmProject(Project project, SonarLintExtension extension) {
        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        var testSourceSets = sourceSets.matching(it -> extension.getTestSourceSets().get().contains(it));
        var mainSourceSets = sourceSets.matching(it -> !testSourceSets.contains(it));

        sourceSets.all(sourceSet -> {
            var taskName = sourceSet.getTaskName("sonarlint", null);
            project.getTasks().register(taskName, SonarLint.class, task -> {
                task.setDescription(format("Run SonarLint analysis for %s classes", sourceSet.getName()));

                task.dependsOn(sourceSet.getClassesTaskName());
                task.dependsOn(sourceSet.getOutput());

                task.dependsOn(sourceSet.getAllSource());
                task.source(sourceSet.getAllSource());

                var sourceSetCompileTasks = project.getTasks().withType(AbstractCompile.class)
                    .matching(compileTask -> isSourceSetTask(sourceSet, compileTask));
                task.dependsOn(sourceSetCompileTasks);
                task.source(project.provider(() ->
                    sourceSetCompileTasks.stream()
                        .map(compileTask -> Optional.of(compileTask)
                            .filter(HasCompileOptions.class::isInstance)
                            .map(HasCompileOptions.class::cast)
                            .map(HasCompileOptions::getOptions)
                            .map(CompileOptions::getGeneratedSourceOutputDirectory)
                        )
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(toList())
                ));

                task.getIsTest().convention(project.provider(() -> testSourceSets.contains(sourceSet)));

                task.java(java -> {
                    var compileJavaTask = project.getTasks()
                        .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);

                    java.getJvm().convention(
                        compileJavaTask
                            .flatMap(JavaCompile::getJavaCompiler)
                            .map(JavaCompiler::getMetadata)
                    );

                    java.getRelease().convention(
                        compileJavaTask
                            .map(JavaCompile::getOptions)
                            .flatMap(CompileOptions::getRelease)
                            .map(JavaLanguageVersion::of)
                            .orElse(
                                compileJavaTask
                                    .map(JavaCompile::getSourceCompatibility)
                                    .map(JavaVersion::toVersion)
                                    .map(JavaVersion::getMajorVersion)
                                    .map(JavaLanguageVersion::of)
                            )
                            .orElse(
                                java.getJvm()
                                    .map(JavaInstallationMetadata::getLanguageVersion)
                            )
                    );

                    java.getEnablePreview().convention(
                        compileJavaTask
                            .map(JavaCompile::getOptions)
                            .map(CompileOptions::getAllCompilerArgs)
                            .map(args -> args.stream().anyMatch("--enable-preview"::equals))
                    );

                    BooleanSupplier isMain = () -> !task.getIsTest().get();

                    setConventionOrFrom(java.getMainOutputDirectories(), project.provider(() ->
                        isMain.getAsBoolean()
                            ? getOutputDirs(sourceSet)
                            : getOutputDirs(mainSourceSets)
                    ));

                    setConventionOrFrom(java.getMainClasspath(), project.provider(() ->
                        isMain.getAsBoolean()
                            ? getLibraries(sourceSet)
                            : getLibraries(mainSourceSets)
                    ));

                    setConventionOrFrom(java.getTestOutputDirectories(), project.provider(() ->
                        isMain.getAsBoolean()
                            ? getOutputDirs(testSourceSets)
                            : getOutputDirs(sourceSet)
                    ));

                    setConventionOrFrom(java.getTestClasspath(), project.provider(() ->
                        isMain.getAsBoolean()
                            ? getLibraries(testSourceSets)
                            : getLibraries(sourceSet)
                    ));

                    java.getDisableRulesConflictingWithLombok().convention(project.provider(() ->
                        project.getConfigurations()
                            .getByName(sourceSet.getCompileClasspathConfigurationName())
                            .getResolvedConfiguration()
                            .getLenientConfiguration()
                            .getAllModuleDependencies()
                            .stream()
                            .map(dep -> dep.getModuleGroup() + ':' + dep.getModuleName())
                            .anyMatch("org.projectlombok:lombok"::equals)
                    ));
                });

                var checkstyleTaskName = sourceSet.getTaskName("checkstyle", null);
                task.getCheckstyleConfig().convention(project.getLayout().file(project.provider(() ->
                    project.getTasks().withType(Checkstyle.class)
                        .matching(it -> it.getName().equals(checkstyleTaskName))
                        .stream().findFirst()
                        .map(Checkstyle::getConfigFile)
                        .orElse(null)
                )));
            });

            project.getTasks().named("check", check -> check.dependsOn(taskName));
        });
    }

    @Nullable
    private static final TypedVoidMethod1<ConfigurableFileCollection, Object[]> CONFIGURABLE_FILE_COLLECTION_CONVENTION
        = findMethod(ConfigurableFileCollection.class, "convention", Object[].class);

    private static void setConventionOrFrom(ConfigurableFileCollection files, Object... paths) {
        if (CONFIGURABLE_FILE_COLLECTION_CONVENTION != null) {
            CONFIGURABLE_FILE_COLLECTION_CONVENTION.invoke(files, paths);
        } else {
            files.setFrom(paths);
        }
    }

    private FileCollection getOutputDirs(Iterable<? extends SourceSet> sourceSets) {
        var result = getObjects().fileCollection();
        for (var sourceSet : sourceSets) {
            var output = sourceSet.getOutput();
            result.from(output);
            Optional.ofNullable(output.getResourcesDir()).ifPresent(result::from);
            result.from(output.getDirs());
        }
        return result;
    }

    private FileCollection getOutputDirs(SourceSet sourceSet) {
        return getOutputDirs(List.of(sourceSet));
    }

    private FileCollection getLibraries(Iterable<? extends SourceSet> sourceSets) {
        var result = getObjects().fileCollection();
        for (var sourceSet : sourceSets) {
            result.from(sourceSet.getCompileClasspath());
        }
        return result;
    }

    private FileCollection getLibraries(SourceSet sourceSet) {
        return getLibraries(List.of(sourceSet));
    }


    @Inject
    protected abstract DependencyHandler getDependencies();

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    @Inject
    protected abstract ObjectFactory getObjects();

}
