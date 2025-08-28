package name.remal.gradle_plugins.sonarlint.internal.server;

public class SonarLintServerException extends RuntimeException {

    public SonarLintServerException(String message) {
        super(message);
    }

    public SonarLintServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public SonarLintServerException(Throwable cause) {
        super(cause);
    }

}
