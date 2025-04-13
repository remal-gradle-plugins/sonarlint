package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PUBLIC;

import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceHelp;

@CustomLog
@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class SonarLintHelpPropertiesWorkAction
    extends AbstractSonarLintHelpWorkAction {

    @Override
    protected void executeImpl(SonarLintServiceHelp service) {
        var propertiesDoc = service.collectPropertiesDocumentation();
        logger.quiet(propertiesDoc.renderToText());
    }

}
