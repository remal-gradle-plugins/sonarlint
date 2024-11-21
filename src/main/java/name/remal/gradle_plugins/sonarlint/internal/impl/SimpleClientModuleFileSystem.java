package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;

@RequiredArgsConstructor
class SimpleClientModuleFileSystem implements ClientModuleFileSystem {

    private final Iterable<? extends ClientInputFile> filesToAnalyze;

    @Override
    public Stream<ClientInputFile> files(String suffix, InputFile.Type type) {
        return files()
            .filter(file -> file.relativePath().endsWith(suffix))
            .filter(file -> file.isTest() == (type == InputFile.Type.TEST));
    }

    @Override
    public Stream<ClientInputFile> files() {
        return StreamSupport.stream(filesToAnalyze.spliterator(), false)
            .map(ClientInputFile.class::cast);
    }

}
