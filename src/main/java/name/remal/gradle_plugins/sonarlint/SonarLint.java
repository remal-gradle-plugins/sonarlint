package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.ANALYSE;
import static name.remal.gradle_plugins.toolkit.ExtensionContainerUtils.findExtension;
import static name.remal.gradle_plugins.toolkit.ReportContainerUtils.setTaskReportDestinationsAutomatically;
import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP;

import java.io.File;
import java.util.concurrent.Callable;
import lombok.Getter;
import lombok.val;
import name.remal.gradle_plugins.toolkit.tasks.BaseSourceVerificationReportingTask;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
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

    @Getter
    @SuppressWarnings("checkstyle:MemberName")
    private final BaseSonarLintInternals $internals = getProject().getObjects().newInstance(
        BaseSonarLintInternals.class,
        this
    );

    @TaskAction
    public void execute() {
        BaseSonarLintActions.execute(this, ANALYSE);
    }

}
