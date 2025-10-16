package name.remal.gradle_plugins.sonarlint;

import static org.gradle.api.plugins.HelpTasksPlugin.HELP_GROUP;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.ParameterizedType;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "This is a help task that only produces console output")
@UntrackedTask(because = "This is a help task that only produces console output")
public abstract class AbstractSonarLintHelpTask<WorkAction extends AbstractSonarLintHelpTaskWorkAction>
    extends AbstractSonarLintTask {

    {
        setGroup(HELP_GROUP);
        setImpliesSubProjects(true);
        getOutputs().doNotCacheIf("Produces only non-cacheable console output", __ -> true);
        getOutputs().upToDateWhen(__ -> false);
    }

    @TaskAction
    public final void execute() {
        checkCoreResolvedDependencies();

        var workQueue = createWorkQueue();
        workQueue.submit(getWorkActionClass(), params -> {
            params.getPluginFiles().from(getPluginFiles());
            params.getLanguagesToProcess().set(getLanguages().getLanguagesToProcess());
        });
    }

    @Internal
    @SuppressWarnings("unchecked")
    protected Class<WorkAction> getWorkActionClass() {
        var typeToken = TypeToken.of(this.getClass());
        var superTypeToken = typeToken.getSupertype(AbstractSonarLintHelpTask.class);
        var type = superTypeToken.getType();
        if (type instanceof ParameterizedType) {
            var parameterizedType = (ParameterizedType) type;
            return (Class<WorkAction>) TypeToken.of(parameterizedType.getActualTypeArguments()[0]).getRawType();
        } else {
            throw new AssertionError("Not a ParameterizedType: " + type);
        }
    }

}
