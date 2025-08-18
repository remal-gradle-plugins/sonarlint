package name.remal.gradle_plugins.sonarlint;

import static com.google.common.io.Files.getFileExtension;
import static groovy.lang.Closure.DELEGATE_FIRST;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.internal.SonarLintLanguageIncludes.getLanguageIncludes;
import static name.remal.gradle_plugins.toolkit.ClosureUtils.configureWith;
import static name.remal.gradle_plugins.toolkit.FileTreeElementUtils.createFileTreeElement;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getCodeFormattingPathsFor;
import static name.remal.gradle_plugins.toolkit.LayoutUtils.getRootDirOf;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.PathUtils.normalizePath;
import static name.remal.gradle_plugins.toolkit.ReportContainerUtils.createReportContainerFor;
import static name.remal.gradle_plugins.toolkit.ReportUtils.getReportDestination;
import static name.remal.gradle_plugins.toolkit.ReportUtils.isReportEnabled;
import static name.remal.gradle_plugins.toolkit.xml.DomUtils.streamNodeList;
import static name.remal.gradle_plugins.toolkit.xml.XmlUtils.parseXml;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;
import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.Getter;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.EditorConfig;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.PathIsOutOfRootPathException;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.work.ChangeType;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@CacheableTask
public abstract class SonarLint
    extends AbstractSonarLintTask<SonarLintAnalyzeWorkActionParams, SonarLintAnalyzeWorkAction>
    implements PatternFilterable, VerificationTask, Reporting<SonarLintReports> {

    private static final String SONAR_LIST_PROPERTY_DELIMITER = ",";
    private static final String SONAR_JAVA_JDK_HOME_PROPERTY = "sonar.java.jdkHome";
    private static final String SONAR_JAVA_SOURCE_PROPERTY = "sonar.java.source";
    private static final String SONAR_JAVA_ENABLE_PREVIEW_PROPERTY = "sonar.java.enablePreview";
    private static final String SONAR_JAVA_BINARIES = "sonar.java.binaries";
    private static final String SONAR_JAVA_LIBRARIES = "sonar.java.libraries";
    private static final String SONAR_JAVA_TEST_BINARIES = "sonar.java.test.binaries";
    private static final String SONAR_JAVA_TEST_LIBRARIES = "sonar.java.test.libraries";


    {
        setGroup(VERIFICATION_GROUP);
    }


    //#region sources

    private final ConfigurableFileCollection sourceFiles = getObjects().fileCollection();

    public void setSource(Object source) {
        sourceFiles.setFrom(source);
    }

    public SonarLint source(Object... sources) {
        sourceFiles.from(sources);
        return this;
    }


    private final LazyValue<FileTree> sources = lazyValue(this::createSourceFileTree);

    private FileTree createSourceFileTree() {
        var sources = sourceFiles.getAsFileTree();

        sources = sources.matching(patternSet);

        sources = sources.matching(filter -> {
            var languageIncludes = getLanguageIncludes(getSettings().getSonarProperties().get());
            getLanguages().getLanguagesToProcess().forEach(lang -> {
                var includes = languageIncludes.get(lang);
                filter.include(includes);
            });
        });

        if (getSettings().getIsGeneratedCodeIgnored().getOrElse(true)) {
            sources = sources.matching(filter -> {
                var allBuildDirectories = getAllProjectsBuildDirectories().getFiles();
                if (allBuildDirectories.isEmpty()) {
                    return;
                }

                filter.exclude(element -> {
                    var file = element.getFile();
                    return allBuildDirectories.stream()
                        .anyMatch(dir -> file.toPath().startsWith(dir.toPath()));
                });
            });
        }

        return sources;
    }

    @Incremental
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(RELATIVE)
    protected final FileTree getSources() {
        return sources.get();
    }


    @Internal
    protected abstract ConfigurableFileCollection getAllProjectsBuildDirectories();

    {
        getAllProjectsBuildDirectories().from(getProviders().provider(() ->
            getProject().getRootProject().getAllprojects().stream()
                .map(project -> project.getLayout().getBuildDirectory())
                .collect(toUnmodifiableList())
        ));
    }

    //#endregion


    //#region PatternFilterable

    private final PatternFilterable patternSet = new PatternSet();

    @Input
    @org.gradle.api.tasks.Optional
    @Override
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    @Input
    @org.gradle.api.tasks.Optional
    @Override
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    @Override
    public SonarLint setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    @Override
    public SonarLint setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    @Override
    public SonarLint include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public SonarLint include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public SonarLint include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public SonarLint include(
        @DelegatesTo(value = FileTreeElement.class, strategy = DELEGATE_FIRST)
        Closure includeSpec
    ) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public SonarLint exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public SonarLint exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public SonarLint exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public SonarLint exclude(
        @DelegatesTo(value = FileTreeElement.class, strategy = DELEGATE_FIRST)
        Closure excludeSpec
    ) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    //#endregion


    //#region Public properties

    @Internal
    public abstract DirectoryProperty getRootDir();

    {
        getRootDir().fileProvider(getProviders().provider(() -> getRootDirOf(getProject())));
    }

    @Input
    protected final String getRootDirPath() {
        return getRootDir().get().getAsFile().getPath();
    }

    @Input
    public abstract Property<Boolean> getIsTest();

    {
        getIsTest().convention(false);
    }

    @Nested
    public abstract SonarLintJavaSettings getJava();

    public void java(Action<SonarLintJavaSettings> action) {
        action.execute(getJava());
    }

    {
        dependsOn(getJava().getMainOutputDirectories());
        dependsOn(getJava().getMainClasspath());
        dependsOn(getJava().getTestOutputDirectories());
        dependsOn(getJava().getTestClasspath());
    }

    @InputFile
    @org.gradle.api.tasks.Optional
    @PathSensitive(RELATIVE)
    public abstract RegularFileProperty getCheckstyleConfig();

    //#endregion


    //#region VerificationTask

    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        getSettings().getIgnoreFailures().set(ignoreFailures);
    }

    @Override
    @Internal
    public boolean getIgnoreFailures() {
        return getSettings().getIgnoreFailures().get();
    }

    //#endregion


    //#region Reporting

    @Getter(onMethod_ = {@Nested})
    private final SonarLintReports reports = createReportContainerFor(this);

    @Override
    public SonarLintReports reports(
        @DelegatesTo(value = SonarLintReports.class, strategy = DELEGATE_FIRST)
        Closure configureAction
    ) {
        configureWith(reports, configureAction);
        return reports;
    }

    @Override
    public SonarLintReports reports(Action<? super SonarLintReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    private Provider<File> getSonarLintReportFile(Function<SonarLintReports, Report> reportGetter) {
        return getProviders().provider(() -> {
            var report = reportGetter.apply(reports);
            if (isReportEnabled(report)) {
                return getReportDestination(report);
            }

            return null;
        });
    }

    //#endregion


    //#region Hidden properties

    @InputFiles
    @org.gradle.api.tasks.Optional
    @PathSensitive(RELATIVE)
    protected abstract ConfigurableFileCollection getCodeFormattingFiles();

    {
        getCodeFormattingFiles().setFrom(getCodeFormattingPathsFor(getProject()));
    }

    //#endregion


    @TaskAction
    public final void execute(@Nullable InputChanges inputChanges) {
        var workQueue = createWorkQueue();
        workQueue.submit(getWorkActionClass(), params ->
            configureWorkActionParams(params, inputChanges)
        );
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    @SuppressWarnings("java:S2259")
    void configureWorkActionParams(
        SonarLintAnalyzeWorkActionParams workActionParams,
        @Nullable InputChanges inputChanges
    ) {
        super.configureWorkActionParams(workActionParams, inputChanges);

        workActionParams.getHomeDirectory().set(new File(getTemporaryDir(), "home"));
        workActionParams.getWorkDirectory().set(new File(getTemporaryDir(), "work"));
        workActionParams.getRootDirectory().set(getRootDir());


        var sourceFiles = collectSourceFiles(inputChanges);
        workActionParams.getSourceFiles().set(sourceFiles);


        var settings = getSettings();

        Map<String, @Nullable String> sonarProperties = new LinkedHashMap<>();
        addJavaProperties(sonarProperties);
        sonarProperties.putAll(settings.getSonarProperties().get());
        addRuleByPathIgnoreProperties(sonarProperties);
        sonarProperties.keySet().removeIf(Objects::isNull);
        sonarProperties.values().removeIf(Objects::isNull);

        var automaticallyDisabledRules = new LinkedHashMap<String, String>();
        disableRulesConflictingWithLombok(automaticallyDisabledRules);
        disableRulesFromCheckstyleConfig(automaticallyDisabledRules);

        workActionParams.getSonarProperties().set(sonarProperties);
        workActionParams.getEnabledRules().set(settings.getRules().getEnabled());
        workActionParams.getDisabledRules().set(settings.getRules().getDisabled());
        workActionParams.getAutomaticallyDisabledRules().set(automaticallyDisabledRules);
        workActionParams.getRulesProperties().set(settings.getRules().getProperties());


        workActionParams.getIsIgnoreFailures().set(getIgnoreFailures());
        workActionParams.getWithDescription().set(settings.getLogging().getWithDescription());
        workActionParams.getXmlReportLocation().fileProvider(getSonarLintReportFile(SonarLintReports::getXml));
        workActionParams.getHtmlReportLocation().fileProvider(getSonarLintReportFile(SonarLintReports::getHtml));
    }

    @SuppressWarnings("java:S3776")
    private Collection<SourceFile> collectSourceFiles(@Nullable InputChanges inputChanges) {
        var sourceFiles = new ArrayList<SourceFile>();
        var processedFiles = new LinkedHashSet<File>();
        var rootDirPath = normalizePath(getRootDir().get().getAsFile().toPath());
        boolean isTest = getIsTest().get();
        var editorConfig = new EditorConfig(rootDirPath);
        Consumer<FileTreeElement> consumer = details -> {
            var file = normalizeFile(details.getFile());
            if (!processedFiles.add(file)) {
                return;
            }

            var relativePath = details.getRelativePath().toString();
            var filePath = file.toPath();
            if (filePath.startsWith(rootDirPath)) {
                relativePath = rootDirPath.relativize(filePath).toString().replace(File.separatorChar, '/');
            }

            final String charsetName;
            {
                Map<String, String> editorConfigProperties;
                try {
                    editorConfigProperties = editorConfig.getPropertiesFor(file);
                } catch (PathIsOutOfRootPathException e) {
                    var extension = getFileExtension(file.getName());
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

            sourceFiles.add(SourceFile.builder()
                .file(file)
                .relativePath(relativePath)
                .test(isTest)
                .encoding(charsetName)
                .build()
            );
        };

        if (inputChanges != null && inputChanges.isIncremental() && !getIgnoreFailures()) {
            inputChanges.getFileChanges(getSources()).forEach(change -> {
                if (change.getChangeType() == ChangeType.REMOVED) {
                    return;
                }

                var relativePath = RelativePath.parse(true, change.getNormalizedPath());
                var details = createFileTreeElement(change.getFile(), relativePath);
                consumer.accept(details);
            });

        } else {
            getSources().visit(details -> {
                if (!details.isDirectory()) {
                    consumer.accept(details);
                }
            });
        }

        return sourceFiles;
    }

    @Contract(mutates = "param1")
    @SuppressWarnings("java:S2259")
    private void addJavaProperties(Map<String, @Nullable String> sonarProperties) {
        sonarProperties.put(SONAR_JAVA_JDK_HOME_PROPERTY,
            getJava().getJvm()
                .map(JavaInstallationMetadata::getInstallationPath)
                .map(Directory::getAsFile)
                .map(File::getAbsolutePath)
                .getOrNull()
        );

        sonarProperties.put(SONAR_JAVA_SOURCE_PROPERTY,
            getJava().getRelease()
                .map(JavaLanguageVersion::asInt)
                .map(String::valueOf)
                .getOrNull()
        );

        sonarProperties.put(SONAR_JAVA_ENABLE_PREVIEW_PROPERTY,
            getJava().getEnablePreview()
                .map(value -> value ? true : null)
                .map(String::valueOf)
                .getOrNull()
        );

        ImmutableMap.<String, Function<SonarLintJavaSettings, ConfigurableFileCollection>>of(
            SONAR_JAVA_BINARIES, SonarLintJavaSettings::getMainOutputDirectories,
            SONAR_JAVA_LIBRARIES, SonarLintJavaSettings::getMainClasspath,
            SONAR_JAVA_TEST_BINARIES, SonarLintJavaSettings::getTestOutputDirectories,
            SONAR_JAVA_TEST_LIBRARIES, SonarLintJavaSettings::getTestClasspath
        ).forEach((property, fileCollectionGetter) ->
            sonarProperties.put(property,
                StreamSupport.stream(fileCollectionGetter.apply(getJava()).spliterator(), false)
                    .filter(File::exists)
                    .map(File::getAbsolutePath)
                    .distinct()
                    .collect(joining(SONAR_LIST_PROPERTY_DELIMITER))
            )
        );
    }

    @Contract(mutates = "param1")
    private void addRuleByPathIgnoreProperties(Map<String, @Nullable String> sonarProperties) {
        var settings = getSettings();
        settings.getIgnoredPaths().get().forEach(ignoredPath ->
            addRuleByPathIgnoreProperties(
                sonarProperties,
                "ignore_all",
                "*",
                ignoredPath
            )
        );

        settings.getRules().getRulesSettings().get().forEach((ruleId, ruleSettings) ->
            ruleSettings.getIgnoredPaths().get().forEach(ignoredPath ->
                addRuleByPathIgnoreProperties(
                    sonarProperties,
                    "ignore_rule",
                    String.valueOf(ruleId),
                    ignoredPath
                )
            )
        );
    }

    @Contract(mutates = "param1")
    @SuppressWarnings("java:S2259")
    private static void addRuleByPathIgnoreProperties(
        Map<String, @Nullable String> sonarProperties,
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
                multicriteria += SONAR_LIST_PROPERTY_DELIMITER + multicriteriaId;
            }
            sonarProperties.put("sonar.issue.ignore.multicriteria", multicriteria);

            break;
        }
    }

    @Contract(mutates = "param1")
    private void disableRulesConflictingWithLombok(Map<String, String> automaticallyDisabledRules) {
        if (!getJava().getDisableRulesConflictingWithLombok().getOrElse(false)) {
            return;
        }

        var disabledRules = new LinkedHashSet<String>();
        if (getJava().getRelease().get().asInt() <= 9) {
            // An iteration on a Collection should be performed on the type handled by the Collection
            disabledRules.add("java:S4838");
        }

        disabledRules.forEach(rule ->
            automaticallyDisabledRules.putIfAbsent(rule, "Conflicts with Lombok")
        );
    }

    @Contract(mutates = "param1")
    private void disableRulesFromCheckstyleConfig(Map<String, String> automaticallyDisabledRules) {
        var checkstyleConfigFile = getCheckstyleConfig().getAsFile().getOrNull();
        if (checkstyleConfigFile == null || !checkstyleConfigFile.isFile()) {
            return;
        }

        final Document document;
        try {
            document = parseXml(checkstyleConfigFile);
        } catch (Exception e) {
            getLogger().error(e.toString(), e);
            return;
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

        var disabledRules = new LinkedHashSet<String>();
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

        disabledRules.forEach(rule ->
            automaticallyDisabledRules.putIfAbsent(rule, "Checkstyle config: " + checkstyleConfigFile)
        );
    }

}
