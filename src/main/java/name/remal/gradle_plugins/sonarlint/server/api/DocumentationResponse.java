package name.remal.gradle_plugins.sonarlint.server.api;

import name.remal.gradle_plugins.sonarlint.internal.Documentation;

interface DocumentationResponse<Doc extends Documentation> extends ApiResponse {

    Doc getDocumentation();

}
