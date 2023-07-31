package name.remal.gradle_plugins.sonarlint;

import static com.google.common.io.Files.getFileExtension;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.exists;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeLanguages;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeProperties;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeRules;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeRulesProperties;
import static name.remal.gradle_plugins.sonarlint.NodeJsVersions.LATEST_NODEJS_LTS_VERSION;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.getSonarDependency;
import static name.remal.gradle_plugins.sonarlint.SonarLintForkOptions.IS_FORK_ENABLED_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.SourceFile.newSourceFileBuilder;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.JavaLauncherUtils.getJavaLauncherProviderFor;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultFalse;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultTrue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.PathUtils.normalizePath;
import static name.remal.gradle_plugins.toolkit.PredicateUtils.not;
import static name.remal.gradle_plugins.toolkit.xml.DomUtils.streamNodeList;
import static name.remal.gradle_plugins.toolkit.xml.XmlUtils.parseXml;

import com.google.common.base.Splitter;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.EditorConfig;
import name.remal.gradle_plugins.toolkit.FileUtils;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.PathIsOutOfRootPathException;
import name.remal.gradle_plugins.toolkit.ReportUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.Directory;
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
import org.jetbrains.annotations.Contract;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@CustomLog
@NoArgsConstructor(access = PRIVATE)
abstract class BaseSonarLintActions {

    static final JavaVersion MIN_SUPPORTED_SOAR_JAVA_VERSION = JavaVersion.VERSION_11;

    static final String SONAR_LIST_PROPERTY_DELIMITER = ",";
    static final String SONAR_SOURCE_ENCODING = "sonar.sourceEncoding";
    static final String SONAR_JAVA_JDK_HOME_PROPERTY = "sonar.java.jdkHome";
    static final String SONAR_JAVA_SOURCE_PROPERTY = "sonar.java.source";
    static final String SONAR_JAVA_TARGET_PROPERTY = "sonar.java.target";
    static final String SONAR_JAVA_ENABLE_PREVIEW_PROPERTY = "sonar.java.enablePreview";
    static final String SONAR_JAVA_BINARIES = "sonar.java.binaries";
    static final String SONAR_JAVA_LIBRARIES = "sonar.java.libraries";
    static final String SONAR_JAVA_TEST_BINARIES = "sonar.java.test.binaries";
    static final String SONAR_JAVA_TEST_LIBRARIES = "sonar.java.test.libraries";

    public static void init(BaseSonarLint task) {
        task.getIsGeneratedCodeIgnored().convention(true);

        task.getIsTest().convention(false);

        task.getJavaLauncher().convention(getJavaLauncherProviderFor(task.getProject(), spec -> {
            val minSupportedJavaLanguageVersion = JavaLanguageVersion.of(
                MIN_SUPPORTED_SOAR_JAVA_VERSION.getMajorVersion()
            );
            val javaMajorVersion = spec.getLanguageVersion()
                .orElse(JavaLanguageVersion.of(JavaVersion.current().getMajorVersion()))
                .map(JavaLanguageVersion::asInt)
                .get();
            if (javaMajorVersion < minSupportedJavaLanguageVersion.asInt()) {
                spec.getLanguageVersion().set(minSupportedJavaLanguageVersion);
            }
        }));

        task.onlyIf(__ -> {
            task.getEnabledRules().set(canonizeRules(task.getEnabledRules().getOrNull()));
            task.getDisabledRules().set(canonizeRules(task.getDisabledRules().getOrNull()));
            task.getIncludedLanguages().set(canonizeLanguages(task.getIncludedLanguages().getOrNull()));
            task.getExcludedLanguages().set(canonizeLanguages(task.getExcludedLanguages().getOrNull()));
            task.getSonarProperties().set(canonizeProperties(task.getSonarProperties().getOrNull()));
            task.getRulesProperties().set(canonizeRulesProperties(task.getRulesProperties().getOrNull()));

            return true;
        });
    }

    @SneakyThrows
    @SuppressWarnings("java:S3776")
    public static void execute(BaseSonarLint task, SonarLintCommand command) {
        val sonarProperties = new LinkedHashMap<>(task.getSonarProperties().get());

        task.getIgnoredPaths().get().forEach(ignoredPath ->
            addRuleByPathIgnore(sonarProperties, "ignore_all", "*", ignoredPath)
        );
        task.getRuleIgnoredPaths().get().forEach((ruleId, ignoredPaths) ->
            ignoredPaths.forEach(ignoredPath ->
                addRuleByPathIgnore(sonarProperties, "ignore_rule", ruleId, ignoredPath)
            )
        );

        sonarProperties.computeIfAbsent(SONAR_JAVA_JDK_HOME_PROPERTY, __ ->
            Optional.ofNullable(task.getJavaLauncher().getOrNull())
                .map(JavaLauncher::getMetadata)
                .map(JavaInstallationMetadata::getInstallationPath)
                .map(Directory::getAsFile)
                .filter(File::exists)
                .map(File::getAbsolutePath)
                .orElse(null)
        );
        sonarProperties.computeIfAbsent(SONAR_JAVA_SOURCE_PROPERTY, __ ->
            Optional.ofNullable(task.getJavaLauncher().getOrNull())
                .map(JavaLauncher::getMetadata)
                .map(JavaInstallationMetadata::getLanguageVersion)
                .map(JavaLanguageVersion::asInt)
                .map(JavaVersion::toVersion)
                .map(JavaVersion::toString)
                .orElse(null)
        );
        sonarProperties.computeIfAbsent(SONAR_JAVA_TARGET_PROPERTY, __ ->
            sonarProperties.get(SONAR_JAVA_SOURCE_PROPERTY)
        );

        sonarProperties.values().removeIf(Objects::isNull);


        sonarProperties.keySet().stream()
            .filter(key -> key.endsWith(".binaries")
                || key.endsWith(".libraries")
            )
            .forEach(key -> {
                val value = sonarProperties.get(key);
                val processedValue = Splitter.on(SONAR_LIST_PROPERTY_DELIMITER).splitToStream(value)
                    .map(String::trim)
                    .filter(ObjectUtils::isNotEmpty)
                    .filter(item -> exists(Paths.get(item)))
                    .collect(joining(SONAR_LIST_PROPERTY_DELIMITER));
                sonarProperties.put(key, processedValue);
            });


        final WorkQueue workQueue;
        {
            val workerExecutor = task.get$internals().getWorkerExecutor().get();
            val forkParams = Optional.ofNullable(task.getForkOptions().getOrNull());
            boolean isForkEnabled = forkParams
                .map(SonarLintForkOptions::getEnabled)
                .map(Provider::getOrNull)
                .orElse(IS_FORK_ENABLED_DEFAULT);
            if (!isForkEnabled && JavaVersion.current().compareTo(MIN_SUPPORTED_SOAR_JAVA_VERSION) < 0) {
                logger.warn(
                    "The current Java version ({}) is less than {}, enabling forking for task {}",
                    JavaVersion.current().getMajorVersion(),
                    MIN_SUPPORTED_SOAR_JAVA_VERSION.getMajorVersion(),
                    task.getPath()
                );
                isForkEnabled = true;
            }
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
            params.getSonarLintVersion().set(getSonarLintVersionFor(task));
            params.getProjectDir().set(task.get$internals().getProjectDir());
            params.getIsGeneratedCodeIgnored().set(defaultTrue(task.getIsGeneratedCodeIgnored().getOrNull()));
            params.getBaseGeneratedDirs().add(task.get$internals().getBuildDir());
            params.getHomeDir().set(new File(tempDir, "home"));
            params.getWorkDir().set(new File(tempDir, "work"));
            params.getToolClasspath().from(task.getToolClasspath().getFiles().stream()
                .filter(File::exists)
                .map(FileUtils::normalizeFile)
                .distinct()
                .collect(toList())
            );
            params.getSourceFiles().set(collectSourceFiles(task));
            params.getEnabledRules().set(task.getEnabledRules());
            params.getDisabledRules().set(task.getDisabledRules());
            params.getDisabledRules().addAll(getDisabledRulesFromCheckstyleConfig(task));
            params.getDisabledRules().addAll(getDisabledRulesConflictingWithLombok(task));
            params.getIncludedLanguages().addAll(task.getIncludedLanguages());
            params.getExcludedLanguages().addAll(task.getExcludedLanguages());
            params.getSonarProperties().set(sonarProperties);
            params.getDefaultNodeJsVersion().set(LATEST_NODEJS_LTS_VERSION);
            params.getRulesProperties().set(task.getRulesProperties());
            params.getXmlReportLocation().set(getSonarLintReportFile(task, SonarLintReports::getXml));
            params.getHtmlReportLocation().set(getSonarLintReportFile(task, SonarLintReports::getHtml));
        });
    }

    @Contract(mutates = "param1")
    @SuppressWarnings("UnstableApiUsage")
    private static void addRuleByPathIgnore(
        Map<String, String> sonarProperties,
        String scope,
        String rule,
        String path
    ) {
        int multicriteriaNumber = 0;
        while (true) {
            ++multicriteriaNumber;
            val multicriteriaId = format("_%s_%d", scope, multicriteriaNumber);
            val ruleKey = format("sonar.issue.ignore.multicriteria.%s.ruleKey", multicriteriaId);
            val resourceKey = format("sonar.issue.ignore.multicriteria.%s.resourceKey", multicriteriaId);
            if (sonarProperties.containsKey(ruleKey) || sonarProperties.containsKey(resourceKey)) {
                continue;
            }

            sonarProperties.put(ruleKey, rule);
            sonarProperties.put(resourceKey, path);

            String multicriteria = sonarProperties.get("sonar.issue.ignore.multicriteria");
            if (isEmpty(multicriteria)) {
                multicriteria = multicriteriaId;
            } else {
                multicriteria += ',' + multicriteriaId;
            }
            sonarProperties.put("sonar.issue.ignore.multicriteria", multicriteria);

            break;
        }
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
    private static String getSonarLintVersionFor(BaseSonarLint task) {
        for (val classpathFile : task.getToolClasspath().getFiles()) {
            val matcher = SONARLINT_CORE_FILE_NAME.matcher(classpathFile.getName());
            if (matcher.matches()) {
                return requireNonNull(matcher.group(1));
            }
        }

        return getSonarDependency("sonarlint-core").getVersion();
    }

    private static Collection<SourceFile> collectSourceFiles(BaseSonarLint task) {
        if (task instanceof SourceTask) {
            return collectSourceFiles(
                ((SourceTask) task).getSource(),
                task.get$internals().getRootDir().get().getAsFile().toPath(),
                defaultFalse(task.getIsTest().getOrNull()),
                task.get$internals().getBuildDir().get().getAsFile().toPath()
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
        Set<Path> processedPaths = new LinkedHashSet<>();
        List<SourceFile> sourceFiles = new ArrayList<>();
        fileTree.visit(details -> {
            if (details.isDirectory()) {
                return;
            }

            val path = normalizePath(details.getFile().toPath());
            if (!processedPaths.add(path)) {
                return;
            }

            val isGenerated = path.startsWith(buildDirPath);

            final String charsetName;
            {
                Map<String, String> editorConfigProperties;
                try {
                    editorConfigProperties = editorConfig.getPropertiesFor(path);
                } catch (PathIsOutOfRootPathException e) {
                    val extension = getFileExtension(path.getFileName().toString());
                    if (isEmpty(extension)) {
                        editorConfigProperties = emptyMap();
                    } else {
                        editorConfigProperties = editorConfig.getPropertiesForFileExtension(extension);
                    }
                }

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
            disabledRules.add("java:S100"); // Method names should comply with a naming convention
        }
        if (moduleNames.contains("TypeName")) {
            disabledRules.add("java:S101"); // Class names should comply with a naming convention
            disabledRules.add("java:S114"); // Interface names should comply with a naming convention
        }
        if (moduleNames.contains("LineLength")) {
            disabledRules.add("java:S103"); // Lines should not be too long
        }
        if (moduleNames.contains("FileTabCharacter")) {
            disabledRules.add("java:S105"); // Tabulation characters should not be used
        }
        if (moduleNames.contains("MemberName")) {
            disabledRules.add("java:S116"); // Field names should comply with a naming convention
        }
        if (moduleNames.contains("LocalVariableName")) {
            // Local variable and method parameter names should comply with a naming convention
            disabledRules.add("java:S117");
        }
        if (moduleNames.contains("ClassTypeParameterName")
            || moduleNames.contains("InterfaceTypeParameterName")
            || moduleNames.contains("MethodTypeParameterName")
        ) {
            disabledRules.add("java:S119"); // Type parameter names should comply with a naming convention
        }
        if (moduleNames.contains("PackageName")) {
            disabledRules.add("java:S120"); // Package names should comply with a naming convention
        }
        if (moduleNames.contains("NeedBraces")) {
            disabledRules.add("java:S121"); // Control structures should use curly braces
        }
        if (moduleNames.contains("OneStatementPerLine")) {
            disabledRules.add("java:S122"); // Statements should be on separate lines
        }

        return unmodifiableCollection(disabledRules);
    }

    private static Collection<String> getDisabledRulesConflictingWithLombok(BaseSonarLint task) {
        if (!TRUE.equals(task.getDisableRulesConflictingWithLombok().getOrNull())) {
            return emptyList();
        }

        Collection<String> disabledRules = new ArrayList<>();

        val sourceJavaVersion = getSourceJavaVersion(task);
        if (!sourceJavaVersion.isJava10Compatible()) {
            // An iteration on a Collection should be performed on the type handled by the Collection
            disabledRules.add("java:S4838");
        }

        return unmodifiableCollection(disabledRules);
    }

    private static JavaVersion getSourceJavaVersion(BaseSonarLint task) {
        val properties = task.getSonarProperties().get();
        return Optional.ofNullable(properties.get(SONAR_JAVA_SOURCE_PROPERTY))
            .filter(ObjectUtils::isNotEmpty)
            .map(version -> {
                try {
                    return JavaVersion.toVersion(version);
                } catch (IllegalArgumentException e) {
                    logger.warn("Illegal value of {} Sonar property: {}", SONAR_JAVA_SOURCE_PROPERTY, version);
                    return null;
                }
            })
            .orElse(JavaVersion.current());

    }

    @Nullable
    private static File getSonarLintReportFile(BaseSonarLint task, Function<SonarLintReports, Report> reportGetter) {
        return getSonarLintReports(task)
            .map(reportGetter)
            .filter(report -> !FALSE.equals(report.getRequired().getOrNull()))
            .map(ReportUtils::getReportDestination)
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
