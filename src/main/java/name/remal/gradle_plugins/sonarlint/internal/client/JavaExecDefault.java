package name.remal.gradle_plugins.sonarlint.internal.client;

import static java.lang.Character.isWhitespace;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.Files.readString;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getEnvironmentVariablesToPropagateToForkedProcess;
import static name.remal.gradle_plugins.sonarlint.internal.utils.ForkUtils.getSystemsPropertiesToPropagateToForkedProcess;
import static name.remal.gradle_plugins.toolkit.JacocoJvmArg.parseJacocoJvmArgFromCurrentJvmArgs;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursivelyIgnoringFailure;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.SneakyThrows;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaExecDefault implements JavaExec {

    private static final Logger logger = LoggerFactory.getLogger(JavaExecDefault.class);

    @Override
    @SneakyThrows
    @SuppressWarnings("java:S5443")
    public JavaExecProcess execute(JavaExecParams params) {
        var allArgs = new ArrayList<String>();

        if (!params.getClasspath().isEmpty()) {
            allArgs.add("-cp");
            allArgs.add(params.getClasspath().stream()
                .map(File::getAbsolutePath)
                .collect(joining(File.pathSeparator))
            );
        }

        params.getMaxHeapSize()
            .map(Object::toString)
            .filter(not(String::isEmpty))
            .ifPresent(it -> allArgs.add("-Xmx" + it));

        allArgs.add("--add-opens");
        allArgs.add("java.base/java.lang=ALL-UNNAMED");

        allArgs.add(format("-D%s=%s", "java.awt.headless", true));

        getSystemsPropertiesToPropagateToForkedProcess().forEach(property -> {
            var value = System.getProperty(property);
            if (value != null) {
                allArgs.add(format("-D%s=%s", property, value));
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
                allArgs.add(arg);
            }
        }

        allArgs.add(params.getMainClass());

        allArgs.addAll(params.getArguments());

        var cliArgumentFile = createTempFile(getClass().getSimpleName() + "-", ".arg");
        try (var writer = newBufferedWriter(cliArgumentFile, UTF_8)) {
            for (var i = 0; i < allArgs.size(); i++) {
                if (i > 0) {
                    writer.write('\n');
                }

                writer.write(escapeCliArgumentFileLine(allArgs.get(i)));
            }
        }
        if (logger.isWarnEnabled()) {
            logger.warn("cliArgumentFile:\n{}", readString(cliArgumentFile));
        }


        var processBuilder = new ProcessBuilder(
            params.getExecutable().getAbsolutePath(),
            "@" + cliArgumentFile
        )
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

        return new JavaExecProcessDefault(cliArgumentFile, process, outputFile.toPath());
    }

    private static String escapeCliArgumentFileLine(String line) {
        if (!needsQuoting(line)) {
            return line;
        }

        return '"' + line.replace("\"", "\\\"").replace("\"", "\\\"") + '"';
    }

    private static boolean needsQuoting(String line) {
        if (line.isEmpty()) {
            return true;
        }

        for (var i = 0; i < line.length(); i++) {
            var ch = line.charAt(i);
            if (isWhitespace(ch)
                || ch == '#'
            ) {
                return true;
            }
        }

        return false;
    }

    @Value
    private static class JavaExecProcessDefault implements JavaExecProcess {

        Path commandLineArgumentFile;

        Process process;

        Path outputFile;


        @Override
        public void close() {
            try {
                JavaExecProcess.super.close();
            } finally {
                tryToDeleteRecursivelyIgnoringFailure(commandLineArgumentFile);
            }
        }

    }

}
