package name.remal.gradle_plugins.sonarlint;

import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

import java.util.List;
import java.util.Map;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.jvm.toolchain.JavaLauncher;

interface BaseSonarLint extends Task {

    @Classpath
    @InputFiles
    ConfigurableFileCollection getToolClasspath();

    @Input
    Property<Boolean> getIsGeneratedCodeIgnored();

    @Input
    Property<Boolean> getIsTest();

    @org.gradle.api.tasks.Optional
    @Input
    ListProperty<String> getEnabledRules();

    @org.gradle.api.tasks.Optional
    @Input
    ListProperty<String> getDisabledRules();

    @org.gradle.api.tasks.Optional
    @Input
    MapProperty<String, String> getSonarProperties();

    @org.gradle.api.tasks.Optional
    @Input
    MapProperty<String, Map<String, String>> getRulesProperties();

    @org.gradle.api.tasks.Optional
    @Input
    ListProperty<String> getIgnoredPaths();

    @org.gradle.api.tasks.Optional
    @Input
    MapProperty<String, List<String>> getRuleIgnoredPaths();


    @org.gradle.api.tasks.Optional
    @InputFile
    @PathSensitive(RELATIVE)
    RegularFileProperty getCheckstyleConfig();

    @Input
    Property<Boolean> getDisableRulesConflictingWithLombok();


    @Nested
    @org.gradle.api.tasks.Optional
    Property<JavaLauncher> getJavaLauncher();

}
