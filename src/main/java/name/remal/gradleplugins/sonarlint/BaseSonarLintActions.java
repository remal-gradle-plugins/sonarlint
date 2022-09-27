package name.remal.gradleplugins.sonarlint;

import static com.google.common.io.ByteStreams.toByteArray;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Math.toIntExact;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradleplugins.sonarlint.CanonizationUtils.canonizeProperties;
import static name.remal.gradleplugins.sonarlint.CanonizationUtils.canonizeRules;
import static name.remal.gradleplugins.sonarlint.CanonizationUtils.canonizeRulesProperties;
import static name.remal.gradleplugins.sonarlint.NodeJsVersions.LATEST_NODEJS_LTS_VERSION;
import static name.remal.gradleplugins.sonarlint.SonarDependencies.getSonarDependency;
import static name.remal.gradleplugins.sonarlint.shared.RunnerParams.newRunnerParamsBuilder;
import static name.remal.gradleplugins.sonarlint.shared.SourceFile.newSourceFileBuilder;
import static name.remal.gradleplugins.toolkit.ExtensionContainerUtils.findExtension;
import static name.remal.gradleplugins.toolkit.ExtensionContainerUtils.getExtension;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradleplugins.toolkit.PathUtils.normalizePath;
import static name.remal.gradleplugins.toolkit.PluginManagerUtils.withAnyOfPlugins;
import static name.remal.gradleplugins.toolkit.PredicateUtils.not;
import static name.remal.gradleplugins.toolkit.ProjectUtils.getTopLevelDirOf;
import static name.remal.gradleplugins.toolkit.ServiceRegistryUtils.getService;
import static name.remal.gradleplugins.toolkit.git.GitUtils.findGitRepositoryRootFor;
import static name.remal.gradleplugins.toolkit.xml.DomUtils.streamNodeList;
import static name.remal.gradleplugins.toolkit.xml.XmlUtils.parseXml;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradleplugins.sonarlint.shared.RunnerCommand;
import name.remal.gradleplugins.sonarlint.shared.SourceFile;
import name.remal.gradleplugins.toolkit.EditorConfig;
import name.remal.gradleplugins.toolkit.ObjectUtils;
import name.remal.gradleplugins.toolkit.PathUtils;
import name.remal.gradleplugins.toolkit.Version;
import name.remal.gradleplugins.toolkit.classpath.ClasspathFiles;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@CustomLog
@NoArgsConstructor(access = PRIVATE)
abstract class BaseSonarLintActions {

    public static void init(BaseSonarLint task) {
        val project = task.getProject();

        task.getIsTest().convention(false);

        val javaToolchainService = getService(project, JavaToolchainService.class);
        val currentJavaLauncherProvider = javaToolchainService.launcherFor(spec ->
            spec.getLanguageVersion().set(JavaLanguageVersion.of(JavaVersion.current().getMajorVersion()))
        );
        task.getJavaLauncher().convention(currentJavaLauncherProvider);
        withAnyOfPlugins(project.getPluginManager(), "java-base", "java", __ -> {
            val javaPluginExtension = getExtension(project, JavaPluginExtension.class);
            val toolchain = javaPluginExtension.getToolchain();
            task.getJavaLauncher().convention(
                javaToolchainService
                    .launcherFor(toolchain)
                    .orElse(currentJavaLauncherProvider)
            );
        });

        task.onlyIf(__ -> {
            task.getEnabledRules().set(canonizeRules(task.getEnabledRules().getOrNull()));
            task.getDisabledRules().set(canonizeRules(task.getDisabledRules().getOrNull()));
            task.getSonarProperties().set(canonizeProperties(task.getSonarProperties().getOrNull()));
            task.getRulesProperties().set(canonizeRulesProperties(task.getRulesProperties().getOrNull()));

            return true;
        });
    }

    @SneakyThrows
    public static void execute(BaseSonarLint task, RunnerCommand runnerCommand) {
        val sonarProperties = new LinkedHashMap<>(task.getSonarProperties().get());

        sonarProperties.computeIfAbsent("sonar.java.jdkHome", __ ->
            Optional.ofNullable(task.getJavaLauncher().getOrNull())
                .map(JavaLauncher::getMetadata)
                .map(JavaInstallationMetadata::getInstallationPath)
                .map(Directory::getAsFile)
                .filter(File::exists)
                .map(File::getAbsolutePath)
                .orElse(null)
        );
        sonarProperties.computeIfAbsent("sonar.java.source", __ ->
            Optional.ofNullable(task.getJavaLauncher().getOrNull())
                .map(JavaLauncher::getMetadata)
                .map(JavaInstallationMetadata::getLanguageVersion)
                .map(JavaLanguageVersion::asInt)
                .map(JavaVersion::toVersion)
                .map(JavaVersion::toString)
                .orElse(null)
        );
        sonarProperties.computeIfAbsent("sonar.java.target", __ -> sonarProperties.get("sonar.java.source"));

        sonarProperties.computeIfAbsent("sonar.nodejs.version", __ -> LATEST_NODEJS_LTS_VERSION);

        sonarProperties.values().removeIf(Objects::isNull);


        val project = task.getProject();
        val tempDir = normalizePath(task.getTemporaryDir().toPath());
        val runnerParamsFile = tempDir.resolve("runner-params.json");

        newRunnerParamsBuilder()
            .ignoreFailures(task instanceof VerificationTask && ((VerificationTask) task).getIgnoreFailures())
            .command(runnerCommand)
            .sonarLintMajorVersion(getSonarLintMajorVersion(task))
            .projectDir(normalizePath(project.getProjectDir().toPath()))
            .homeDir(createDirectories(tempDir.resolve("home")))
            .workDir(createDirectories(tempDir.resolve("work")))
            .toolClasspath(task.getToolClasspath().getFiles().stream()
                .filter(File::exists)
                .map(File::toPath)
                .map(PathUtils::normalizePath)
                .collect(toList())
            )
            .files(collectSourceFiles(task))
            .enabledRules(task.getEnabledRules().get())
            .disabledRules(task.getDisabledRules().get())
            .addAllDisabledRules(getDisabledRulesFromCheckstyleConfig(task))
            .addAllDisabledRules(getDisabledRulesConflictingWithLombok(task))
            .sonarProperties(sonarProperties)
            .rulesProperties(task.getRulesProperties().get())
            .xmlReportLocation(getSonarLintReportPath(task, SonarLintReports::getXml))
            .htmlReportLocation(getSonarLintReportPath(task, SonarLintReports::getHtml))
            .build()
            .writeTo(runnerParamsFile);


        final WorkQueue workQueue;
        {
            val workerExecutor = getService(task.getProject(), WorkerExecutor.class);
            val forkParams = Optional.ofNullable(findExtension(project, SonarLintExtension.class))
                .map(SonarLintExtension::getFork);
            val isForkEnabled = forkParams
                .map(SonarLintForkOptions::getEnabled)
                .map(Provider::getOrNull)
                .orElse(true);
            if (isForkEnabled) {
                workQueue = workerExecutor.processIsolation(spec -> {
                    spec.getForkOptions().setExecutable(task.getJavaLauncher().get()
                        .getExecutablePath()
                        .getAsFile()
                        .getAbsolutePath()
                    );
                    spec.getForkOptions().setMaxHeapSize(forkParams
                        .map(SonarLintForkOptions::getMaxHeapSize)
                        .map(Provider::getOrNull)
                        .orElse(null)
                    );
                    spec.getClasspath().from(task.getToolClasspath());
                });

            } else {
                workQueue = workerExecutor.classLoaderIsolation(spec -> {
                    spec.getClasspath().from(task.getToolClasspath());
                });
            }
        }

        workQueue.submit(SonarLintAction.class, params -> {
            params.getRunnerParamsFile().fileValue(runnerParamsFile.toFile());
        });
    }

    private static Path getRepositoryRootPath(BaseSonarLint task) {
        val topLevelDir = getTopLevelDirOf(task.getProject());
        val repositoryRoot = findGitRepositoryRootFor(topLevelDir);
        if (repositoryRoot != null) {
            return repositoryRoot;
        }

        return topLevelDir;
    }

    @SneakyThrows
    private static int getSonarLintMajorVersion(BaseSonarLint task) {
        val javaVersion = Optional.ofNullable(task.getJavaLauncher().getOrNull())
            .map(JavaLauncher::getMetadata)
            .map(JavaInstallationMetadata::getLanguageVersion)
            .map(JavaLanguageVersion::asInt)
            .map(JavaVersion::toVersion)
            .orElseGet(JavaVersion::current);
        val classpath = new ClasspathFiles(task.getToolClasspath(), javaVersion);
        try (val inputStream = classpath.openStream("sonarlint-api-version.txt")) {
            if (inputStream != null) {
                val bytes = toByteArray(inputStream);
                val content = new String(bytes, UTF_8).trim();
                if (!content.isEmpty()) {
                    val version = Version.parse(content);
                    return toIntExact(version.getNumber(0));
                }
            }
        }

        val configuration = task.getProject().getConfigurations().findByName("sonarlint");
        if (configuration != null) {
            val version = configuration.getResolvedConfiguration()
                .getLenientConfiguration()
                .getAllModuleDependencies()
                .stream()
                .filter(dep -> Objects.equals(dep.getModuleGroup(), "org.sonarsource.sonarlint.core"))
                .filter(dep -> Objects.equals(dep.getModuleName(), "sonarlint-core"))
                .map(ResolvedDependency::getModuleVersion)
                .filter(ObjectUtils::isNotEmpty)
                .map(Version::parse)
                .findFirst()
                .orElse(null);
            if (version != null) {
                return toIntExact(version.getNumber(0));
            }
        }

        val versionString = getSonarDependency("sonarlint-core").getVersion();
        val version = Version.parse(versionString);
        return toIntExact(version.getNumber(0));
    }

    private static Collection<SourceFile> collectSourceFiles(BaseSonarLint task) {
        if (task instanceof SourceTask) {
            return collectSourceFiles(
                ((SourceTask) task).getSource(),
                getRepositoryRootPath(task),
                TRUE.equals(task.getIsTest().getOrNull())
            );
        }

        return emptyList();
    }

    private static Collection<SourceFile> collectSourceFiles(
        FileTree fileTree,
        Path repositoryRootPath,
        boolean isTest
    ) {
        val editorConfig = new EditorConfig(repositoryRootPath);
        List<SourceFile> sourceFiles = new ArrayList<>();
        fileTree.visit(details -> {
            if (details.isDirectory()) {
                return;
            }

            val path = normalizePath(details.getFile().toPath());

            final String charsetName;
            {
                val editorConfigProperties = editorConfig.getPropertiesFor(path);
                val charsetString = editorConfigProperties.get("charset");
                if (isNotEmpty(charsetString)) {
                    charsetName = charsetString.toUpperCase();
                } else {
                    charsetName = UTF_8.name();
                }
            }

            sourceFiles.add(newSourceFileBuilder()
                .absolutePath(path.toString())
                .relativePath(details.getPath())
                .test(isTest)
                .charsetName(charsetName)
                .build()
            );
        });
        return sourceFiles;
    }

    private static Collection<String> getDisabledRulesFromCheckstyleConfig(BaseSonarLint task) {
        val checkstyleConfigFile = Optional.ofNullable(task.getCheckstyleConfig().getAsFile().getOrNull())
            .map(File::getAbsoluteFile)
            .filter(File::isFile)
            .orElse(null);
        if (checkstyleConfigFile == null) {
            return emptyList();
        }


        final Document document;
        try {
            document = parseXml(checkstyleConfigFile);
        } catch (Exception e) {
            logger.error(e.toString(), e);
            return emptyList();
        }

        val moduleElements = streamNodeList(document.getElementsByTagName("module"))
            .filter(Element.class::isInstance)
            .map(Element.class::cast)
            .filter(not(module -> "ignore".equalsIgnoreCase(module.getAttribute("severity"))))
            .collect(toList());
        val moduleNames = moduleElements.stream()
            .map(module -> module.getAttribute("name"))
            .filter(ObjectUtils::isNotEmpty)
            .collect(toSet());

        Collection<String> disabledRules = new ArrayList<>();

        if (moduleNames.contains("MethodName")) {
            disabledRules.add("java:S100");
        }
        if (moduleNames.contains("TypeName")) {
            disabledRules.add("java:S101");
            disabledRules.add("java:S114");
        }
        if (moduleNames.contains("LineLength")) {
            disabledRules.add("java:S103");
        }
        if (moduleNames.contains("???")) { // FIXME
            disabledRules.add("java:S105"); // Tabulation characters should not be used
        }
        if (moduleNames.contains("MemberName")) {
            disabledRules.add("java:S116");
        }
        if (moduleNames.contains("LocalVariableName")) {
            disabledRules.add("java:S117");
        }
        if (moduleNames.contains("ClassTypeParameterName")
            || moduleNames.contains("InterfaceTypeParameterName")
            || moduleNames.contains("MethodTypeParameterName")
        ) {
            disabledRules.add("java:S119");
        }
        if (moduleNames.contains("PackageName")) {
            disabledRules.add("java:S120");
        }
        if (moduleNames.contains("???")) { // FIXME
            disabledRules.add("java:S121"); // Control structures should use curly braces
        }
        if (moduleNames.contains("???")) { // FIXME
            disabledRules.add("java:S122"); // Statements should be on separate lines
        }

        return unmodifiableCollection(disabledRules);
    }

    private static Collection<String> getDisabledRulesConflictingWithLombok(BaseSonarLint task) {
        if (!TRUE.equals(task.getDisableRulesConflictingWithLombok().getOrNull())) {
            return emptyList();
        }

        return ImmutableList.of(

        );
    }

    @Nullable
    private static Path getSonarLintReportPath(BaseSonarLint task, Function<SonarLintReports, Report> reportGetter) {
        return getSonarLintReports(task)
            .map(reportGetter)
            .filter(report -> !FALSE.equals(report.getRequired().getOrNull()))
            .map(Report::getOutputLocation)
            .map(Provider::getOrNull)
            .map(FileSystemLocation::getAsFile)
            .map(File::toPath)
            .map(PathUtils::normalizePath)
            .orElse(null);
    }

    private static Optional<SonarLintReports> getSonarLintReports(BaseSonarLint task) {
        if (!(task instanceof Reporting)) {
            return Optional.empty();
        }

        val reports = (SonarLintReports) ((Reporting<?>) task).getReports();
        return Optional.of(reports);
    }

}
