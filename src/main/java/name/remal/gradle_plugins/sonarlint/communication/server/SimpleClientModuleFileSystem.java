package name.remal.gradle_plugins.sonarlint.communication.server;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;

@RequiredArgsConstructor
class SimpleClientModuleFileSystem implements ClientModuleFileSystem {

    private final Iterable<ClientInputFile> filesToAnalyze;

    @Override
    @SuppressWarnings("BadImport")
    public Stream<ClientInputFile> files(String suffix, Type type) {
        return files()
            .filter(file -> file.relativePath().endsWith(suffix))
            .filter(file -> file.isTest() == (type == InputFile.Type.TEST));
    }

    @Override
    public Stream<ClientInputFile> files() {
        return StreamSupport.stream(filesToAnalyze.spliterator(), false);
    }

}
