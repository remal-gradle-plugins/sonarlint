package name.remal.gradleplugins.sonarlint;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static name.remal.gradleplugins.toolkit.reflection.MembersFinder.getStaticMethod;
import static name.remal.gradleplugins.toolkit.reflection.ReflectionUtils.packageNameOf;

import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.gradle.workers.WorkAction;

@NoArgsConstructor(onConstructor_ = {@Inject})
@CustomLog
abstract class SonarLintAction implements WorkAction<SonarLintActionParameters> {

    @Override
    @SneakyThrows
    public void execute() {
        logger.debug("Running {}", SonarLintAction.class.getSimpleName());
        val parameters = getParameters();

        ClassLoader classLoader = currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = SonarLintAction.class.getClassLoader();
        }

        val mainClass = Class.forName(
            format("%s.runner.Main", packageNameOf(SonarLintPlugin.class)),
            true,
            classLoader
        );

        val mainMethod = getStaticMethod(mainClass, "main", String[].class);
        mainMethod.invoke(new String[]{
            parameters.getRunnerParamsFile().getAsFile().get().getAbsolutePath()
        });
    }

}
