package name.remal.gradle_plugins.sonarlint;

import static groovy.lang.Closure.DELEGATE_FIRST;
import static name.remal.gradle_plugins.toolkit.ClosureUtils.configureWith;
import static name.remal.gradle_plugins.toolkit.ReportContainerUtils.createReportContainerFor;
import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import lombok.Getter;
import lombok.Setter;
import org.gradle.api.Action;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.VerificationTask;

@CacheableTask
public abstract class SonarLint
    extends AbstractSonarLint<SonarLintWorkActionParams, SonarLintWorkAction>
    implements VerificationTask, Reporting<SonarLintReports> {

    {
        setGroup(VERIFICATION_GROUP);
    }


    @Getter(onMethod_ = {@Nested})
    private final SonarLintReports reports = createReportContainerFor(this);

    @Override
    public SonarLintReports reports(@DelegatesTo(strategy = DELEGATE_FIRST) Closure closure) {
        configureWith(reports, closure);
        return reports;
    }

    @Override
    public SonarLintReports reports(Action<? super SonarLintReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }


    @Setter
    private boolean ignoreFailures;

    @Override
    @Input
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }


    @Override
    @SuppressWarnings("ClassEscapesDefinedScope")
    protected void configureWorkActionParams(SonarLintWorkActionParams workActionParams) {
        super.configureWorkActionParams(workActionParams);


    }

}
