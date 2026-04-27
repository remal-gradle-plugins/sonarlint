package name.remal.gradle_plugins.sonarlint;

/**
 * Severity levels for SonarLint issues, ordered from most to least severe.
 *
 * <p>The declaration order is significant: it defines the threshold comparison used by
 * {@link SonarLintSettings#getFailOnSeverity()}. An issue with severity X fails the build
 * if {@code X.compareTo(threshold) <= 0}, so {@link #ERROR} must remain first and
 * {@link #INFO} last.
 */
public enum SonarLintIssueSeverity {

    ERROR,

    WARNING,

    INFO,

}
