package name.remal.gradle_plugins.sonarlint;

import java.util.Map;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

interface SonarLintAnalyzeWorkActionParams extends AbstractSonarLintWorkActionParams {

    DirectoryProperty getHomeDirectory();

    DirectoryProperty getWorkDirectory();

    DirectoryProperty getRootDirectory();


    ListProperty<SourceFile> getSourceFiles();

    MapProperty<String, String> getSonarProperties();

    SetProperty<String> getEnabledRules();

    SetProperty<String> getDisabledRules();

    MapProperty<String, String> getAutomaticallyDisabledRules();

    MapProperty<String, Map<String, String>> getRulesProperties();


    Property<Boolean> getIsIgnoreFailures();

    Property<Boolean> getWithDescription();

    RegularFileProperty getXmlReportLocation();

    RegularFileProperty getHtmlReportLocation();

}
