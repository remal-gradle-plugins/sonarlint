package name.remal.gradle_plugins.sonarlint;

import lombok.CustomLog;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceHelp;
import name.remal.gradle_plugins.sonarlint.internal.impl.SonarLintServiceHelpParams;

@CustomLog
abstract class SonarLintHelpPropertiesWorkAction
    extends AbstractSonarLintHelpWorkAction {

    @Override
    protected void executeImpl(SonarLintServiceHelpParams serviceParams) {
        try (var service = new SonarLintServiceHelp(serviceParams)) {
            var propertiesDoc = service.collectPropertiesDocumentation();
            logger.quiet(propertiesDoc.renderToText());
        }
    }

}
