package name.remal.gradle_plugins.sonarlint.internal;

import static lombok.AccessLevel.PRIVATE;
import static org.gradle.api.tasks.PathSensitivity.ABSOLUTE;

import java.io.File;
import java.io.Serializable;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;

@Value
@Builder
@RequiredArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PRIVATE, force = true)
public class NodeJsFound implements NodeJsInfo, Serializable {

    //@java.io.Serial
    private static final long serialVersionUID = 1;


    @NonNull
    @InputFile
    @PathSensitive(ABSOLUTE)
    File executable;

    @NonNull
    @Input
    String version;

}
