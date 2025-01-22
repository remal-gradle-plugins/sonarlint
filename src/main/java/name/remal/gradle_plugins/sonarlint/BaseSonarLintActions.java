package name.remal.gradle_plugins.sonarlint;

import static com.google.common.io.Files.getFileExtension;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.exists;
import static java.util.Arrays.stream;
import static java.util.Collections.addAll;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeLanguages;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeProperties;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeRules;
import static name.remal.gradle_plugins.sonarlint.CanonizationUtils.canonizeRulesProperties;
import static name.remal.gradle_plugins.sonarlint.SonarDependencies.getSonarDependency;
import static name.remal.gradle_plugins.sonarlint.SonarLintForkOptions.IS_FORK_ENABLED_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.SonarLintPluginBuildInfo.SONARLINT_PLUGIN_ID;
import static name.remal.gradle_plugins.sonarlint.internal.SourceFile.newSourceFileBuilder;
import static name.remal.gradle_plugins.toolkit.ExtensionContainerUtils.findExtension;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.JavaLauncherUtils.getJavaLauncherProviderFor;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultFalse;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.defaultTrue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.PathUtils.normalizePath;
import static name.remal.gradle_plugins.toolkit.PredicateUtils.not;
import static name.remal.gradle_plugins.toolkit.TaskUtils.doBeforeTaskExecution;
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
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.SonarLanguage;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.EditorConfig;
import name.remal.gradle_plugins.toolkit.FileUtils;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.PathIsOutOfRootPathException;
import name.remal.gradle_plugins.toolkit.ReportUtils;
import name.remal.gradle_plugins.toolkit.Version;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.LogLevel;
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

@NoArgsConstructor(access = PRIVATE)
abstract class BaseSonarLintActions {

    static final JavaVersion MIN_SUPPORTED_SONAR_JAVA_VERSION = JavaVersion.VERSION_17;

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
    static final String SONAR_NODEJS_EXECUTABLE = "sonar.nodejs.executable";
    static final String SONAR_NODEJS_EXECUTABLE_TS = "sonar.typescript.node";
    static final String SONAR_NODEJS_VERSION = "sonar.nodejs.version";

    static final List<String> LANGUAGES_REQUIRING_NODEJS = stream(SonarLanguage.values())
        .filter(SonarLanguage::isRequireNodeJs)
        .map(SonarLanguage::getName)
        .collect(toUnmodifiableList());

    public static void init(BaseSonarLint task) {
        task.getIsTest().convention(false);

        var project = task.getProject();
        var extensionProvider = project.provider(() -> findExtension(project, SonarLintExtension.class));
        task.getIsGeneratedCodeIgnored().convention(extensionProvider
            .flatMap(SonarLintExtension::getIsGeneratedCodeIgnored)
            .orElse(true)
        );
        task.getEnabledRules().addAll(extensionProvider
            .map(SonarLintExtension::getRules)
            .flatMap(SonarLintRulesSettings::getEnabled)
            .orElse(emptySet())
        );
        task.getDisabledRules().addAll(extensionProvider
            .map(SonarLintExtension::getRules)
            .flatMap(SonarLintRulesSettings::getDisabled)
            .orElse(emptySet())
        );
        task.getIncludedLanguages().addAll(extensionProvider
            .map(SonarLintExtension::getLanguages)
            .flatMap(SonarLintLanguagesSettings::getIncludes)
            .orElse(emptyList())
        );
        task.getExcludedLanguages().addAll(extensionProvider
            .map(SonarLintExtension::getLanguages)
            .flatMap(SonarLintLanguagesSettings::getExcludes)
            .orElse(emptyList())
        );
        task.getSonarProperties().putAll(extensionProvider
            .flatMap(SonarLintExtension::getSonarProperties)
            .map(CanonizationUtils::canonizeProperties)
            .orElse(emptyMap())
        );
        task.getRulesProperties().putAll(extensionProvider
            .map(SonarLintExtension::getRules)
            .map(SonarLintRulesSettings::buildProperties)
            .map(CanonizationUtils::canonizeRulesProperties)
            .orElse(emptyMap())
        );
        task.getIgnoredPaths().addAll(extensionProvider
            .flatMap(SonarLintExtension::getIgnoredPaths)
            .orElse(emptyList())
        );
        task.getRuleIgnoredPaths().putAll(extensionProvider
            .map(SonarLintExtension::getRules)
            .map(SonarLintRulesSettings::buildIgnoredPaths)
            .orElse(emptyMap())
        );

        task.getJavaLauncher().convention(getJavaLauncherProviderFor(task.getProject(), spec -> {
            var minSupportedJavaLanguageVersion = JavaLanguageVersion.of(
                MIN_SUPPORTED_SONAR_JAVA_VERSION.getMajorVersion()
            );
            var javaMajorVersion = spec.getLanguageVersion()
                .orElse(JavaLanguageVersion.of(JavaVersion.current().getMajorVersion()))
                .map(JavaLanguageVersion::asInt)
                .get();
            if (javaMajorVersion < minSupportedJavaLanguageVersion.asInt()) {
                spec.getLanguageVersion().set(minSupportedJavaLanguageVersion);
            }
        }));

        doBeforeTaskExecution(task, __ -> {
            task.getEnabledRules().set(canonizeRules(task.getEnabledRules().getOrNull()));
            task.getDisabledRules().set(canonizeRules(task.getDisabledRules().getOrNull()));
            task.getIncludedLanguages().set(canonizeLanguages(task.getIncludedLanguages().getOrNull()));
            task.getExcludedLanguages().set(canonizeLanguages(task.getExcludedLanguages().getOrNull()));
            task.getSonarProperties().set(canonizeProperties(task.getSonarProperties().getOrNull()));
            task.getRulesProperties().set(canonizeRulesProperties(task.getRulesProperties().getOrNull()));
        });
    }

    @SneakyThrows
    @SuppressWarnings("java:S3776")
    public static void execute(BaseSonarLint task, SonarLintCommand command) {
        var sonarProperties = new LinkedHashMap<>(task.getSonarProperties().get());

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
                var value = sonarProperties.get(key);
                var processedValue = Splitter.on(SONAR_LIST_PROPERTY_DELIMITER).splitToStream(value)
                    .map(String::trim)
                    .filter(ObjectUtils::isNotEmpty)
                    .filter(item -> exists(Paths.get(item)))
                    .collect(joining(SONAR_LIST_PROPERTY_DELIMITER));
                sonarProperties.put(key, processedValue);
            });


        Stream.of(
            SONAR_NODEJS_EXECUTABLE,
            SONAR_NODEJS_EXECUTABLE_TS,
            SONAR_NODEJS_VERSION
        ).forEach(property -> {
            var value = sonarProperties.remove(property);
            if (value != null) {
                task.getLogger().warn(
                    "`{}` Sonar property is configured, but it's not used."
                        + " Use `sonarLint.nodeJs.nodeJsExecutable = ...` instead.",
                    property
                );
            }
        });

        var nodeJsInfo = getNodeJsInfoAndLogIssues(task);
        var additionalExcludedLanguages = new ArrayList<String>();
        if (task instanceof SourceTask && nodeJsInfo == null) {
            additionalExcludedLanguages.addAll(LANGUAGES_REQUIRING_NODEJS);
        }


        final WorkQueue workQueue;
        {
            var workerExecutor = task.get$internals().getWorkerExecutor().get();
            var forkParams = Optional.ofNullable(task.getForkOptions().getOrNull());
            boolean isForkEnabled = forkParams
                .map(SonarLintForkOptions::getEnabled)
                .map(Provider::getOrNull)
                .orElse(IS_FORK_ENABLED_DEFAULT);
            if (!isForkEnabled && JavaVersion.current().compareTo(MIN_SUPPORTED_SONAR_JAVA_VERSION) < 0) {
                task.getLogger().warn(
                    "The current Java version ({}) is less than {}, enabling forking for task {}",
                    JavaVersion.current().getMajorVersion(),
                    MIN_SUPPORTED_SONAR_JAVA_VERSION.getMajorVersion(),
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
                    spec.getClasspath().from(task.getCoreClasspath());
                });

            } else {
                workQueue = workerExecutor.classLoaderIsolation(spec -> {
                    spec.getClasspath().from(task.getCoreClasspath());
                });
            }
        }

        workQueue.submit(SonarLintAction.class, params -> {
            var sonarLintVersion = getSonarLintVersionFor(task);
            if (Version.parse(sonarLintVersion).compareTo(SONARLINT_DEFAULT_VERSION) > 0) {
                task.getLogger().log(
                    defaultFalse(task.getLoggingOptions().flatMap(SonarLintLoggingOptions::getHideWarnings).getOrNull())
                        ? LogLevel.INFO
                        : LogLevel.QUIET,
                    "SonarLint version is greater than with what `{}` plugin was built with."
                        + " It can cause unpredicted issues.",
                    SONARLINT_PLUGIN_ID
                );
            }

            var tempDir = normalizeFile(task.getTemporaryDir());

            params.getIsIgnoreFailures().set(isIgnoreFailures(task));
            params.getCommand().set(command);
            params.getSonarLintVersion().set(sonarLintVersion);
            params.getProjectDir().set(task.get$internals().getProjectDir());
            params.getIsGeneratedCodeIgnored().set(defaultTrue(task.getIsGeneratedCodeIgnored().getOrNull()));
            params.getBaseGeneratedDirs().add(task.get$internals().getBuildDir());
            params.getHomeDir().set(new File(tempDir, "home"));
            params.getWorkDir().set(new File(tempDir, "work"));
            params.getCoreClasspath().from(task.getCoreClasspath().getFiles().stream()
                .filter(File::exists)
                .collect(toCollection(LinkedHashSet::new))
            );
            params.getPluginsClasspath().from(task.getPluginsClasspath().getFiles().stream()
                .filter(File::exists)
                .collect(toCollection(LinkedHashSet::new))
            );
            params.getSourceFiles().set(collectSourceFiles(task));
            params.getEnabledRules().set(task.getEnabledRules());
            params.getDisabledRules().set(task.getDisabledRules());
            params.getDisabledRules().addAll(getDisabledRulesFromCheckstyleConfig(task));
            params.getDisabledRules().addAll(getDisabledRulesConflictingWithLombok(task));
            params.getIncludedLanguages().addAll(task.getIncludedLanguages());
            params.getExcludedLanguages().addAll(task.getExcludedLanguages());
            params.getExcludedLanguages().addAll(canonizeLanguages(additionalExcludedLanguages));
            params.getSonarProperties().set(sonarProperties);
            params.getRulesProperties().set(task.getRulesProperties());
            params.getNodeJsInfo().set(nodeJsInfo);
            params.getXmlReportLocation().set(getSonarLintReportFile(task, SonarLintReports::getXml));
            params.getHtmlReportLocation().set(getSonarLintReportFile(task, SonarLintReports::getHtml));
            params.getWithDescription().set(task.getLoggingOptions()
                .flatMap(SonarLintLoggingOptions::getWithDescription)
                .orElse(true)
            );
        });
    }

    @Contract(mutates = "param1")
    private static void addRuleByPathIgnore(
        Map<String, String> sonarProperties,
        String scope,
        String rule,
        String path
    ) {
        int multicriteriaNumber = 0;
        while (true) {
            ++multicriteriaNumber;
            var multicriteriaId = format("_%s_%d", scope, multicriteriaNumber);
            var ruleKey = format("sonar.issue.ignore.multicriteria.%s.ruleKey", multicriteriaId);
            var resourceKey = format("sonar.issue.ignore.multicriteria.%s.resourceKey", multicriteriaId);
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

    @Nullable
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    private static NodeJsFound getNodeJsInfoAndLogIssues(BaseSonarLint task) {
        if (!(task instanceof SourceTask)) {
            return null;
        }

        var configuredNodeJsInfo = task.get$internals().getConfiguredNodeJsInfo().getOrNull();
        if (configuredNodeJsInfo != null) {
            return configuredNodeJsInfo;
        }

        var relativePathsRequiringNodeJs = task.get$internals().getRelativePathsRequiringNodeJs().getOrElse(emptySet());
        if (relativePathsRequiringNodeJs.isEmpty()) {
            return null;
        }

        var nodeJsInfo = task.get$internals().getDetectedNodeJsInfo().getOrNull();
        if (nodeJsInfo != null) {
            return nodeJsInfo;
        }


        var logNodeJsNotFound = defaultTrue(task.getNodeJs()
            .flatMap(SonarLintNodeJs::getLogNodeJsNotFound)
            .getOrNull()
        );
        var configuredNodeJs = task.getNodeJs()
            .flatMap(SonarLintNodeJs::getNodeJsExecutable)
            .getOrNull();
        var detectNodeJs = defaultFalse(task.getNodeJs()
            .flatMap(SonarLintNodeJs::getDetectNodeJs)
            .getOrNull()
        );
        var lines = new ArrayList<String>();
        if (configuredNodeJs != null) {
            addAll(
                lines,
                "Node.js executable configured, but it can't be used. Configured value: " + configuredNodeJs,
                "Potential reasons are:",
                "  * file not found",
                "  * file is not executable",
                "  * not a Node.js command",
                "  * this Node.js does not work on this OS (for example, incompatible system libraries)"
            );

        } else if (detectNodeJs) {
            addAll(
                lines,
                "Node.js can not be detected.",
                "Potential reasons are:",
                "  * this OS and CPU architecture are not supported",
                "  * Node.js does not work on this OS (for example, incompatible system libraries)",
                "",
                "You can configure Node.js by using `sonarLint.nodeJs.nodeJsExecutable = ...`."
            );

        } else {
            addAll(
                lines,
                "Node.js is not configured and its detection is disabled.",
                "",
                "You can configure Node.js by using `sonarLint.nodeJs.nodeJsExecutable = ...`.",
                "Or you can enable Node.js detection by `sonarLint.nodeJs.detectNodeJs = true`."
            );
        }


        addAll(
            lines,
            "",
            join(", ", LANGUAGES_REQUIRING_NODEJS) + " languages are excluded"
                + ", because Sonar requires Node.js to process them.",
            "To hide this message, add `sonarLint.nodeJs.logNodeJsNotFound = false` to your build script."
        );


        lines.add("");

        var maxPathsToLog = 25;
        if (relativePathsRequiringNodeJs.size() > maxPathsToLog) {
            addAll(lines, format(
                "First %d files requiring Node.js (%d total):",
                maxPathsToLog,
                relativePathsRequiringNodeJs.size()
            ));
        } else {
            addAll(lines, "Files requiring Node.js:");
        }
        relativePathsRequiringNodeJs.stream()
            .limit(maxPathsToLog)
            .forEach(path -> lines.add("  * " + path));

        lines.add("");
        lines.add("");


        var lineSeparator = format("%n");
        var message = join(lineSeparator, lines);
        var logLevel = logNodeJsNotFound ? LogLevel.QUIET : LogLevel.INFO;
        task.getLogger().log(logLevel, message);

        return null;
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

    private static final Version SONARLINT_DEFAULT_VERSION = Version.parse(
        getSonarDependency("sonarlint-core").getVersion()
    );

    @SneakyThrows
    private static String getSonarLintVersionFor(BaseSonarLint task) {
        for (var classpathFile : task.getCoreClasspath().getFiles()) {
            var matcher = SONARLINT_CORE_FILE_NAME.matcher(classpathFile.getName());
            if (matcher.matches()) {
                return requireNonNull(matcher.group(1));
            }
        }

        return SONARLINT_DEFAULT_VERSION.toString();
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
        var editorConfig = new EditorConfig(repositoryRootPath);
        Set<Path> processedPaths = new LinkedHashSet<>();
        List<SourceFile> sourceFiles = new ArrayList<>();
        fileTree.visit(details -> {
            if (details.isDirectory()) {
                return;
            }

            var path = normalizePath(details.getFile().toPath());
            if (!processedPaths.add(path)) {
                return;
            }

            var isGenerated = path.startsWith(buildDirPath);

            final String charsetName;
            {
                Map<String, String> editorConfigProperties;
                try {
                    editorConfigProperties = editorConfig.getPropertiesFor(path);
                } catch (PathIsOutOfRootPathException e) {
                    var extension = getFileExtension(path.getFileName().toString());
                    if (isEmpty(extension)) {
                        editorConfigProperties = emptyMap();
                    } else {
                        editorConfigProperties = editorConfig.getPropertiesForFileExtension(extension);
                    }
                }

                var charsetString = editorConfigProperties.get("charset");
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

    @SuppressWarnings("Slf4jFormatShouldBeConst")
    private static Collection<String> getDisabledRulesFromCheckstyleConfig(BaseSonarLint task) {
        var checkstyleConfigFile = Optional.ofNullable(task.getCheckstyleConfig().getAsFile().getOrNull())
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
            task.getLogger().error(e.toString(), e);
            return emptyList();
        }

        var moduleElements = streamNodeList(document.getElementsByTagName("module"))
            .filter(Element.class::isInstance)
            .map(Element.class::cast)
            .filter(not(module -> "ignore".equalsIgnoreCase(module.getAttribute("severity"))))
            .collect(toList());
        var moduleNames = moduleElements.stream()
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

        var sourceJavaVersion = getSourceJavaVersion(task);
        if (!sourceJavaVersion.isJava10Compatible()) {
            // An iteration on a Collection should be performed on the type handled by the Collection
            disabledRules.add("java:S4838");
        }

        return unmodifiableCollection(disabledRules);
    }

    private static JavaVersion getSourceJavaVersion(BaseSonarLint task) {
        var properties = task.getSonarProperties().get();
        return Optional.ofNullable(properties.get(SONAR_JAVA_SOURCE_PROPERTY))
            .filter(ObjectUtils::isNotEmpty)
            .map(version -> {
                try {
                    return JavaVersion.toVersion(version);
                } catch (IllegalArgumentException e) {
                    task.getLogger().warn(
                        "Illegal value of {} Sonar property: {}",
                        SONAR_JAVA_SOURCE_PROPERTY,
                        version
                    );
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

        var reports = (SonarLintReports) ((Reporting<?>) task).getReports();
        return Optional.of(reports);
    }

}
