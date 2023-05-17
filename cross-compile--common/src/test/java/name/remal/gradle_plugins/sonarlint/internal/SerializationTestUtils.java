package name.remal.gradle_plugins.sonarlint.internal;

import static java.lang.Math.multiplyExact;
import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.reflection.MembersFinder.findStaticMethod;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.isNotStatic;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.isStatic;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.packageNameOf;
import static name.remal.gradle_plugins.toolkit.reflection.ReflectionUtils.tryLoadClass;

import com.google.common.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.Contract;

@NoArgsConstructor(access = PRIVATE)
abstract class SerializationTestUtils {

    @Contract("_->param1")
    public static <T> T populateBuilderWithValues(T builder) {
        Class<?> builderClass = builder.getClass();

        Class<?> objectClass = null;
        Method buildMethod = null;
        try {
            buildMethod = builderClass.getMethod("build");
        } catch (NoSuchMethodException ignored) {
            // do nothing
        }
        if (buildMethod != null && isNotStatic(buildMethod)) {
            objectClass = buildMethod.getReturnType();
        }

        for (val method : builderClass.getMethods()) {
            if (method.isSynthetic()
                || isStatic(method)
                || method.getDeclaringClass() == Object.class
                || method.getParameterCount() != 1
            ) {
                continue;
            }

            if (method.getName().equals("from")) {
                val paramType = method.getParameterTypes()[0];
                if (paramType.isAssignableFrom(builderClass)) {
                    continue;
                }
                if (objectClass != null && paramType.isAssignableFrom(objectClass)) {
                    continue;
                }
            }

            try {
                populateBuilderWithValue(builder, method);
            } catch (Throwable e) {
                throw new RuntimeException("Error in populating value to " + method, e);
            }
        }
        return builder;
    }

    @SneakyThrows
    @SuppressWarnings("UnstableApiUsage")
    private static void populateBuilderWithValue(Object builder, Method method) {
        val type = TypeToken.of(builder.getClass()).method(method).getParameters().get(0).getType().getType();
        val value = createValue(type);
        method.invoke(builder, value);
    }

    @SneakyThrows
    private static Object createValue(Type type) {
        if (type instanceof TypeVariable) {
            val boundType = ((TypeVariable<?>) type).getBounds()[0];
            return createValue(boundType);
        } else if (type instanceof WildcardType) {
            val boundType = ((WildcardType) type).getUpperBounds()[0];
            return createValue(boundType);
        }

        val rawType = TypeToken.of(type).getRawType();

        if (rawType == String.class) {
            return nextString();

        } else if (rawType == boolean.class || rawType == Boolean.class) {
            return nextBoolean();

        } else if (rawType == byte.class || rawType == Byte.class) {
            return nextByte();

        } else if (rawType == short.class || rawType == Short.class) {
            return nextShort();

        } else if (rawType == int.class || rawType == Integer.class) {
            return nextInt();

        } else if (rawType == long.class || rawType == Long.class) {
            return nextLong();

        } else if (Enum.class.isAssignableFrom(rawType)) {
            return nextEnumConstants(rawType);

        } else if (rawType.isArray()) {
            val elementType = requireNonNull(rawType.getComponentType());
            val elementsCount = ThreadLocalRandom.current().nextInt(2, 10);
            val values = Array.newInstance(elementType, elementsCount);
            for (int i = 0; i < elementsCount; ++i) {
                val element = createValue(elementType);
                Array.set(values, i, element);
            }
            return values;

        } else if (rawType == File.class) {
            return normalizeFile(new File('/' + nextString()));

        } else if (rawType == Iterable.class
            || rawType == Collection.class
            || rawType == List.class
        ) {
            val elementType = getActualTypeArgument(type, 0);
            val elementsCount = ThreadLocalRandom.current().nextInt(2, 8);
            val values = new ArrayList<>(elementsCount);
            for (int i = 0; i < elementsCount; ++i) {
                val element = createValue(elementType);
                values.add(element);
            }
            return values;

        } else if (rawType == Set.class) {
            val elementType = getActualTypeArgument(type, 0);
            val elementsCount = ThreadLocalRandom.current().nextInt(2, 8);
            val values = new LinkedHashSet<>(elementsCount);
            for (int i = 0; i < elementsCount; ++i) {
                val element = createValue(elementType);
                values.add(element);
            }
            return values;

        } else if (rawType == Map.class) {
            val keyType = getActualTypeArgument(type, 0);
            val valueType = getActualTypeArgument(type, 1);
            val entriesCount = ThreadLocalRandom.current().nextInt(2, 8);
            val values = new LinkedHashMap<>(entriesCount);
            for (int i = 0; i < entriesCount; ++i) {
                val key = createValue(keyType);
                val value = createValue(valueType);
                values.put(key, value);
            }
            return values;

        } else if (rawType == Entry.class) {
            val keyType = getActualTypeArgument(type, 0);
            val valueType = getActualTypeArgument(type, 1);
            val key = createValue(keyType);
            val value = createValue(valueType);
            return new SimpleEntry<>(key, value);
        }

        if (rawType.isInterface() || isAbstract(rawType.getModifiers())) {
            val immutableClass = tryLoadClass(packageNameOf(rawType) + ".Immutable" + rawType.getSimpleName());
            if (immutableClass != null) {
                val builderMethod = findStaticMethod(immutableClass, Object.class, "builder");
                if (builderMethod != null) {
                    val builder = builderMethod.invoke();
                    populateBuilderWithValues(builder);
                    return builder.getClass().getMethod("build").invoke(builder);
                }
            }
        }

        throw new UnsupportedOperationException("Unsupported type: " + type);
    }

    private static String nextString() {
        return nextBigInteger().toString(16);
    }

    private static BigInteger nextBigInteger() {
        val bits = multiplyExact(8, ThreadLocalRandom.current().nextInt(4, 16));
        return new BigInteger(bits, ThreadLocalRandom.current()).abs();
    }

    private static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextInt() >= 0;
    }

    private static Object nextEnumConstants(Class<?> enumType) {
        val enumConstants = requireNonNull(enumType.getEnumConstants());
        val index = ThreadLocalRandom.current().nextInt(0, enumConstants.length);
        return enumConstants[index];
    }

    private static byte nextByte() {
        return (byte) ThreadLocalRandom.current().nextInt(1, Byte.MAX_VALUE - 10);
    }

    private static short nextShort() {
        return (short) ThreadLocalRandom.current().nextInt(1, Short.MAX_VALUE - 10);
    }

    private static int nextInt() {
        return ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 10);
    }

    private static long nextLong() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE - 10);
    }

    private static Type getActualTypeArgument(Type type, int index) {
        if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getActualTypeArguments()[index];
        } else {
            throw new UnsupportedOperationException("Not a ParameterizedType: " + type);
        }
    }

}
