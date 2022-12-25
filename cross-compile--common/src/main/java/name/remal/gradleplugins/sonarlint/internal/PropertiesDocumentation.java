package name.remal.gradleplugins.sonarlint.internal;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.FunctionUtils.toIndentedString;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.val;
import name.remal.gradle_plugins.toolkit.ObjectUtils;

public class PropertiesDocumentation implements Documentation {

    private final SortedMap<String, PropertyDocumentation> properties = new TreeMap<>();

    public void property(String propertyKey, Consumer<PropertyDocumentation> action) {
        val propertyDocumentation = new PropertyDocumentation();
        propertyDocumentation.setName(propertyKey);
        action.accept(propertyDocumentation);
        properties.put(propertyKey, propertyDocumentation);
    }

    @Override
    @SuppressWarnings("java:S3776")
    public String renderToText() {
        if (properties.isEmpty()) {
            return "No properties SonarLint found";
        }

        val message = new StringBuilder();
        properties.forEach((propKey, propDoc) -> {
            if (isNotEmpty(message)) {
                message.append("\n\n");
            }
            message.append(propKey);

            Optional.ofNullable(propDoc.getName())
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(desc -> message.append(" - ").append(desc));

            Optional.ofNullable(propDoc.getDescription())
                .filter(ObjectUtils::isNotEmpty)
                .map(toIndentedString(2))
                .ifPresent(desc -> message.append("\n").append(desc));

            Optional.ofNullable(propDoc.getType())
                .ifPresent(type -> message.append("\n  Type: ").append(type));

            Optional.ofNullable(propDoc.getDefaultValue())
                .filter(ObjectUtils::isNotEmpty)
                .ifPresent(value -> message.append("\n  Default value: ").append(value));
        });

        return message.toString();
    }


    @Data
    @FieldDefaults(level = PRIVATE)
    public static class PropertyDocumentation {

        @Nullable
        String name;

        @Nullable
        String description;

        @Nullable
        String type;

        @Nullable
        String defaultValue;

    }

}
