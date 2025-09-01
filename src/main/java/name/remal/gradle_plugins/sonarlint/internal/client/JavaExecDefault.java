package name.remal.gradle_plugins.sonarlint.internal.client;

import static java.lang.String.format;
import static java.nio.file.Files.createTempFile;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getEnvironmentVariablesToPropagateToForkedProcess;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getSystemsPropertiesToPropagateToForkedProcess;
import static name.remal.gradle_plugins.toolkit.JacocoJvmArg.parseJacocoJvmArgFromCurrentJvmArgs;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.SneakyThrows;
import lombok.Value;

public class JavaExecDefault implements JavaExec {

    @Override
    @SneakyThrows
    @SuppressWarnings("java:S5443")
    public JavaExecProcess execute(JavaExecParams params) {
        var command = new ArrayList<String>();
        command.add(params.getExecutable().getAbsolutePath());

        if (!params.getClasspath().isEmpty()) {
            command.add("-cp");
            command.add(params.getClasspath().stream()
                .map(File::getAbsolutePath)
                .collect(joining(File.pathSeparator))
            );
        }

        params.getMaxHeapSize()
            .map(Object::toString)
            .filter(not(String::isEmpty))
            .ifPresent(it -> command.add("-Xmx" + it));

        command.add("--add-opens");
        command.add("java.base/java.lang=ALL-UNNAMED");

        command.add(format("-D%s=%s", "java.awt.headless", true));

        getSystemsPropertiesToPropagateToForkedProcess().forEach(property -> {
            var value = System.getProperty(property);
            if (value != null) {
                command.add(format("-D%s=%s", property, value));
            }
        });

        var jacocoJvmArg = parseJacocoJvmArgFromCurrentJvmArgs();
        if (jacocoJvmArg != null) {
            jacocoJvmArg.makePathsAbsolute();
            jacocoJvmArg.excludeGradleClasses();
            jacocoJvmArg.append(true);
            jacocoJvmArg.dumpOnExit(true);
            jacocoJvmArg.jmx(false);
            for (var arg : jacocoJvmArg.asArguments()) {
                command.add(arg);
            }
        }

        command.add(params.getMainClass());

        command.addAll(params.getArguments());


        var processBuilder = new ProcessBuilder(command)
            .redirectErrorStream(true);

        var outputFile = createTempFile(getClass().getSimpleName() + "-", ".log").toFile();
        processBuilder.redirectOutput(Redirect.appendTo(outputFile));

        getEnvironmentVariablesToPropagateToForkedProcess().forEach(envName -> {
            var value = System.getenv(envName);
            if (value != null) {
                processBuilder.environment().put(envName, value);
            }
        });

        var process = processBuilder.start();

        return new JavaExecProcessDefault(process, outputFile.toPath());
    }

    @Value
    private static class JavaExecProcessDefault implements JavaExecProcess {
        Process process;
        Path outputFile;
    }

}
