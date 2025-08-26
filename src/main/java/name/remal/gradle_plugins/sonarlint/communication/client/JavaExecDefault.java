package name.remal.gradle_plugins.sonarlint.communication.client;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.util.ArrayList;
import lombok.SneakyThrows;

public class JavaExecDefault implements JavaExec {

    @Override
    @SneakyThrows
    public void execute(JavaExecParams params) {
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
        command.add(params.getMainClass());
        command.addAll(params.getArguments());
        var process = new ProcessBuilder(command);
        process.inheritIO();
        process.start();
    }

}
