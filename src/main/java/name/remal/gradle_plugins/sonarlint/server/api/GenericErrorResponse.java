package name.remal.gradle_plugins.sonarlint.server.api;

import static lombok.AccessLevel.PRIVATE;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
@RequiredArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PRIVATE, force = true)
public class GenericErrorResponse implements ApiResponse {

    @NonNull
    String message;

}
