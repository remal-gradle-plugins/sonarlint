package name.remal.gradle_plugins.sonarlint;

import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.Internal;

public interface SonarLintReports extends ReportContainer<ConfigurableReport> {

    @Internal
    SingleFileReport getXml();

    @Internal
    SingleFileReport getHtml();

}
