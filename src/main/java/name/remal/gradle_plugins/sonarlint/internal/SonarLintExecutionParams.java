package name.remal.gradle_plugins.sonarlint.internal;

import java.util.Map;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.workers.WorkParameters;

public interface SonarLintExecutionParams extends WorkParameters {

    Property<Boolean> getIsIgnoreFailures();

    Property<SonarLintCommand> getCommand();

    DirectoryProperty getProjectDir();

    Property<Boolean> getIsGeneratedCodeIgnored();

    DirectoryProperty getWorkDir();

    ConfigurableFileCollection getPluginsClasspath();

    ListProperty<SourceFile> getSourceFiles();

    SetProperty<String> getEnabledRules();

    SetProperty<String> getDisabledRules();

    SetProperty<String> getIncludedLanguages();

    SetProperty<String> getExcludedLanguages();

    MapProperty<String, String> getSonarProperties();

    MapProperty<String, Map<String, String>> getRulesProperties();

    Property<NodeJsFound> getNodeJsInfo();

    RegularFileProperty getXmlReportLocation();

    RegularFileProperty getHtmlReportLocation();

    Property<Boolean> getWithDescription();

}
