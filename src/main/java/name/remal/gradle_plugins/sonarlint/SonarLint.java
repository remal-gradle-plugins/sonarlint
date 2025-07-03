package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.internal.SonarLintCommand.ANALYSE;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.ReportContainerUtils.setTaskReportDestinationsAutomatically;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;
import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP;

import lombok.Getter;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.tasks.BaseSourceVerificationReportingTask;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

@CacheableTask
public abstract class SonarLint
    extends BaseSourceVerificationReportingTask<SonarLintReports>
    implements BaseSonarLint {

    {
        setGroup(VERIFICATION_GROUP);
        BaseSonarLintActions.init(this);
        setTaskReportDestinationsAutomatically(this);
    }

    @Getter
    @SuppressWarnings("checkstyle:MemberName")
    private final BaseSonarLintInternals $internals = getProject().getObjects().newInstance(
        BaseSonarLintInternals.class,
        this
    );

    @Override
    @Input
    public boolean getIgnoreFailures() {
        return super.getIgnoreFailures();
    }

    @Override
    @Internal
    public FileTree getSource() {
        return super.getSource();
    }

    private final LazyValue<FileTree> cachedSource = lazyValue(this::getSource);

    @Incremental
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(RELATIVE)
    protected final FileTree getCachedSource() {
        return cachedSource.get();
    }

    @TaskAction
    public void execute(InputChanges inputChanges) {
        BaseSonarLintActions.execute(this, ANALYSE, inputChanges);
    }

}
