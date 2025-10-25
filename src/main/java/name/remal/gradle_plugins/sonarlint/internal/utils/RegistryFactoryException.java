package name.remal.gradle_plugins.sonarlint.internal.utils;

public class RegistryFactoryException extends RuntimeException {

    RegistryFactoryException(String message) {
        super(message);
    }

    RegistryFactoryException(String message, Throwable cause) {
        super(message, cause);
    }

}
