package name.remal.gradleplugins.sonarlint.internal;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import java.util.List;
import name.remal.gradleplugins.toolkit.issues.Issue;

public abstract class SonarLintExecutor {

    private SonarLintExecutionParams params;

    protected final SonarLintExecutionParams getParams() {
        return requireNonNull(params, "params");
    }


    @OverridingMethodsMustInvokeSuper
    public void init(SonarLintExecutionParams params) {
        this.params = params;
    }

    /**
     * This method uses generic {@link Object} type, otherwise classes relocation will break compilation.
     *
     * @return a list of {@link Issue}
     */
    @SuppressWarnings("java:S1452")
    public abstract List<?> analyze();

    public abstract RulesDocumentation collectRulesDocumentation();

    public abstract PropertiesDocumentation collectPropertiesDocumentation();


    protected final boolean isIgnored(SourceFile sourceFile) {
        if (sourceFile.isGenerated()) {
            return getParams().getIsGeneratedCodeIgnored().getOrElse(true);
        }

        return false;
    }

}
