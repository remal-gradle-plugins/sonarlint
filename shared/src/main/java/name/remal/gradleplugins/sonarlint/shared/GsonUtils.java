package name.remal.gradleplugins.sonarlint.shared;

import static lombok.AccessLevel.PRIVATE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import java.util.ServiceLoader;
import lombok.NoArgsConstructor;
import lombok.val;

@NoArgsConstructor(access = PRIVATE)
abstract class GsonUtils {

    public static final Gson GSON;

    static {
        val gsonBuilder = new GsonBuilder()
            .setLenient()
            .disableHtmlEscaping()
            .setPrettyPrinting();
        ServiceLoader.load(TypeAdapterFactory.class).forEach(gsonBuilder::registerTypeAdapterFactory);
        GSON = gsonBuilder.create();
    }

}
