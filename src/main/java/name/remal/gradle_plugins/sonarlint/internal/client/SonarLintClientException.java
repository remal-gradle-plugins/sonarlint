package name.remal.gradle_plugins.sonarlint.internal.client;

public class SonarLintClientException extends RuntimeException {

    public SonarLintClientException(String message) {
        super(message);
    }

    public SonarLintClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public SonarLintClientException(Throwable cause) {
        super(cause);
    }

}
