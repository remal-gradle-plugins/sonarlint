package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PUBLIC;

import javax.inject.Inject;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.sonarlint.communication.server.api.SonarLintHelp;

@CustomLog
@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class SonarLintHelpPropertiesWorkAction
    extends AbstractSonarLintHelpTaskWorkAction {

    @Override
    protected void executeImpl(SonarLintHelp service) throws Throwable {
        var propertiesDoc = service.getPropertiesDocumentation();
        logger.quiet(propertiesDoc.renderToText());
    }

}
