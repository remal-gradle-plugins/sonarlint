package name.remal.gradleplugins.sonarlint;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
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
import static name.remal.gradleplugins.sonarlint.internal.SourceFile.newSourceFileBuilder;
import static name.remal.gradleplugins.toolkit.ExtensionContainerUtils.findExtension;
import static name.remal.gradleplugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradleplugins.toolkit.JavaLauncherUtils.getJavaLauncherProviderFor;
import static name.remal.gradleplugins.toolkit.ObjectUtils.defaultFalse;
import static name.remal.gradleplugins.toolkit.ObjectUtils.defaultTrue;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isEmpty;
import static name.remal.gradleplugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradleplugins.toolkit.PathUtils.normalizePath;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradleplugins.sonarlint.internal.SonarLintCommand;
import name.remal.gradleplugins.sonarlint.internal.SourceFile;
import name.remal.gradleplugins.toolkit.EditorConfig;
import name.remal.gradleplugins.toolkit.FileUtils;
import name.remal.gradleplugins.toolkit.ObjectUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@CustomLog
@NoArgsConstructor(access = PRIVATE)
abstract class BaseSonarLintActions {

    public static void init(BaseSonarLint task) {
        task.getIsGeneratedCodeIgnored().convention(true);

        task.getIsTest().convention(false);

        task.getJavaLauncher().convention(getJavaLauncherProviderFor(task.getProject()));

        task.onlyIf(__ -> {
            task.getEnabledRules().set(canonizeRules(task.getEnabledRules().getOrNull()));
            task.getDisabledRules().set(canonizeRules(task.getDisabledRules().getOrNull()));
            task.getSonarProperties().set(canonizeProperties(task.getSonarProperties().getOrNull()));
            task.getRulesProperties().set(canonizeRulesProperties(task.getRulesProperties().getOrNull()));

            return true;
        });
    }

    @SneakyThrows
    @SuppressWarnings("java:S3776")
    public static void execute(BaseSonarLint task, SonarLintCommand command) {
        val generatedCodeIgnored = defaultTrue(task.getIsGeneratedCodeIgnored().getOrNull());
        val sourceFiles = collectSourceFiles(task);


        val sonarProperties = new LinkedHashMap<>(task.getSonarProperties().get());

        if (generatedCodeIgnored) {
            val ignoreMulticriteriaPrevNumber = new AtomicInteger(0);
            sourceFiles.stream()
                .filter(SourceFile::isGenerated)
                .map(SourceFile::getRelativePath)
                .forEach(sourceFileRelativePath -> {
                    while (true) {
                        val multicriteriaId = "_generated_" + ignoreMulticriteriaPrevNumber.incrementAndGet();
                        val ruleKey = format("sonar.issue.ignore.multicriteria.%s.ruleKey", multicriteriaId);
                        val resourceKey = format("sonar.issue.ignore.multicriteria.%s.resourceKey", multicriteriaId);
                        if (sonarProperties.containsKey(ruleKey) || sonarProperties.containsKey(resourceKey)) {
                            continue;
                        }

                        String multicriteria = sonarProperties.get("sonar.issue.ignore.multicriteria");
                        if (isEmpty(multicriteria)) {
                            multicriteria = multicriteriaId;
                        } else {
                            multicriteria += ',' + multicriteriaId;
                        }
                        sonarProperties.put("sonar.issue.ignore.multicriteria", multicriteria);

                        sonarProperties.put(ruleKey, "*");
                        sonarProperties.put(resourceKey, sourceFileRelativePath);

                        break;
                    }
                });
        }

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

        sonarProperties.values().removeIf(Objects::isNull);


        val project = task.getProject();

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
            val tempDir = normalizeFile(task.getTemporaryDir());

            params.getIsIgnoreFailures().set(isIgnoreFailures(task));
            params.getCommand().set(command);
            params.getSonarLintVersion().set(getSonarLintVersion(task));
            params.getProjectDir().set(normalizeFile(project.getProjectDir()));
            params.getIsGeneratedCodeIgnored().set(generatedCodeIgnored);
            params.getBaseGeneratedDirs().add(project.getLayout().getBuildDirectory());
            params.getHomeDir().set(new File(tempDir, "home"));
            params.getWorkDir().set(new File(tempDir, "work"));
            params.getToolClasspath().from(task.getToolClasspath().getFiles().stream()
                .filter(File::exists)
                .map(FileUtils::normalizeFile)
                .distinct()
                .collect(toList())
            );
            params.getSourceFiles().set(sourceFiles);
            params.getEnabledRules().set(task.getEnabledRules());
            params.getDisabledRules().set(task.getDisabledRules());
            params.getDisabledRules().addAll(getDisabledRulesFromCheckstyleConfig(task));
            params.getDisabledRules().addAll(getDisabledRulesConflictingWithLombok(task));
            params.getSonarProperties().set(sonarProperties);
            params.getDefaultNodeJsVersion().set(LATEST_NODEJS_LTS_VERSION);
            params.getRulesProperties().set(task.getRulesProperties());
            params.getXmlReportLocation().set(getSonarLintReportFile(task, SonarLintReports::getXml));
            params.getHtmlReportLocation().set(getSonarLintReportFile(task, SonarLintReports::getHtml));
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

    private static boolean isIgnoreFailures(BaseSonarLint task) {
        if (task instanceof VerificationTask) {
            return ((VerificationTask) task).getIgnoreFailures();
        }
        return false;
    }

    private static final Pattern SONARLINT_CORE_FILE_NAME = Pattern.compile(
        "sonarlint-core-(\\d+(?:\\.\\d+){0,3}).*\\.jar"
    );

    @SneakyThrows
    private static String getSonarLintVersion(BaseSonarLint task) {
        for (val classpathFile : task.getToolClasspath().getFiles()) {
            val matcher = SONARLINT_CORE_FILE_NAME.matcher(classpathFile.getName());
            if (matcher.matches()) {
                return matcher.group(1);
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
                .findFirst()
                .orElse(null);
            if (version != null) {
                return version;
            }
        }

        return getSonarDependency("sonarlint-core").getVersion();
    }

    private static Collection<SourceFile> collectSourceFiles(BaseSonarLint task) {
        if (task instanceof SourceTask) {
            return collectSourceFiles(
                ((SourceTask) task).getSource(),
                getRepositoryRootPath(task),
                defaultFalse(task.getIsTest().getOrNull()),
                normalizePath(task.getProject().getBuildDir().toPath())
            );
        }

        return emptyList();
    }

    private static Collection<SourceFile> collectSourceFiles(
        FileTree fileTree,
        Path repositoryRootPath,
        boolean isTest,
        Path buildDirPath
    ) {
        val editorConfig = new EditorConfig(repositoryRootPath);
        List<SourceFile> sourceFiles = new ArrayList<>();
        fileTree.visit(details -> {
            if (details.isDirectory()) {
                return;
            }

            val path = normalizePath(details.getFile().toPath());

            val isGenerated = path.startsWith(buildDirPath);

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
                .generated(isGenerated)
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
        if (moduleNames.contains("FileTabCharacter")) {
            disabledRules.add("java:S105");
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
        if (moduleNames.contains("NeedBraces")) {
            disabledRules.add("java:S121");
        }
        if (moduleNames.contains("OneStatementPerLine")) {
            disabledRules.add("java:S122");
        }

        return unmodifiableCollection(disabledRules);
    }

    private static Collection<String> getDisabledRulesConflictingWithLombok(BaseSonarLint task) {
        if (!TRUE.equals(task.getDisableRulesConflictingWithLombok().getOrNull())) {
            return emptyList();
        }

        return ImmutableList.of(
            "java:S4838" // An iteration on a Collection should be performed on the type handled by the Collection
        );
    }

    @Nullable
    private static File getSonarLintReportFile(BaseSonarLint task, Function<SonarLintReports, Report> reportGetter) {
        return getSonarLintReports(task)
            .map(reportGetter)
            .filter(report -> !FALSE.equals(report.getRequired().getOrNull()))
            .map(Report::getOutputLocation)
            .map(Provider::getOrNull)
            .map(FileSystemLocation::getAsFile)
            .map(FileUtils::normalizeFile)
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
