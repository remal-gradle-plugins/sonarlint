package name.remal.gradleplugins.sonarlint;

import static name.remal.gradleplugins.sonarlint.shared.RunnerCommand.ANALYSE;
import static name.remal.gradleplugins.toolkit.ExtensionContainerUtils.findExtension;
import static name.remal.gradleplugins.toolkit.ReportContainerUtils.setTaskReportDestinationsAutomatically;
import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP;

import java.io.File;
import java.util.concurrent.Callable;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import name.remal.gradleplugins.toolkit.tasks.BaseSourceVerificationReportingTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
@Getter
@Setter
public abstract class SonarLint
    extends BaseSourceVerificationReportingTask<SonarLintReports>
    implements BaseSonarLint {

    {
        setGroup(VERIFICATION_GROUP);
        BaseSonarLintActions.init(this);

        setTaskReportDestinationsAutomatically(this, (Callable<File>) () -> {
            val sonarLintExtension = findExtension(getProject(), SonarLintExtension.class);
            return sonarLintExtension != null ? sonarLintExtension.getReportsDir() : null;
        });
    }

    private FileCollection toolClasspath = getProject().files();

    @TaskAction
    public void execute() {
        BaseSonarLintActions.execute(this, ANALYSE);
    }

}
