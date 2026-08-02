package dev.lukebemish.polymorphicsignatures.utilities;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/// Utilities for working with enums, or enum-like classes, that are polymorphic on their receiving type
public final class EnumUtils {
    /// Annotates classes which should be treated as enums for the sake of the utilities in this class. In particular,
    /// annotated classes must:
    /// - Have a public instance {@code String name()} method
    /// - Have a public instance {@code int ordinal()} method
    /// - Have a public static {@code T[] values()} method
    /// - Have a public static {@code T valueOf(String)} method that throws an {@link IllegalArgumentException} on non-matching input
    /// - Implement {@link java.lang.constant.Constable}, {@link Comparable}, and {@link java.io.Serializable}
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EnumIsh {}

    /// {@return the value of an enum(-like) class with the provided name}
    /// @param value the name to find
    /// @throws IllegalArgumentException if the provided name is not valid
    /// @see Enum#valueOf(Class, String)
    @PolymorphicSignature("$valueOf")
    public native static <T> T valueOf(String value) throws IllegalArgumentException;

    /// {@return the value of an enum(-like) class with the provided name, or {@code null}}
    /// @param value the name to find
    /// @see #valueOf(String)
    @PolymorphicSignature("$valueOf")
    public native static <T> @Nullable T tryValueOf(String value);

    /// {@return the possible values of the receiving type}
    /// Equivalent to calling the implicit {@code public static T[] values()} method of the enum(-like) class
    @PolymorphicSignature("$values")
    public native static <T> T[] values();

    // --------------------------------
    // INTERNAL DETAILS BELOW THIS LINE
    // --------------------------------
    //
    // This class contains public metafactories. These are marked internal, as user code should not reference them.
    // However, they are still binary API as the polymorphic signature methods above cause references to these to be
    // included in consumer bytecode!

    @ApiStatus.Internal
    @ImplementationMetafactory
    public static CallSite $valueOf(MethodHandles.Lookup lookup, String name, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        var targetType = methodType.returnType();
        checkEnumIsh(targetType);
        var valueOf = lookup.findStatic(targetType, "valueOf", MethodType.methodType(targetType, String.class));
        if (name.equals("valueOf")) {
            return new ConstantCallSite(valueOf);
        }
        var constantNull = MethodHandles.dropArguments(
            MethodHandles.constant(targetType, null),
            0,
            IllegalArgumentException.class,
            String.class
        );
        return new ConstantCallSite(MethodHandles.catchException(valueOf, IllegalArgumentException.class, constantNull));
    }

    private static void checkEnumIsh(Class<?> targetType) {
        if (!Enum.class.isAssignableFrom(targetType) && !targetType.isAnnotationPresent(EnumIsh.class)) {
            throw new IllegalArgumentException("Not enum-ish: "+ targetType);
        }
    }

    @ApiStatus.Internal
    @ImplementationMetafactory
    public static CallSite $values(MethodHandles.Lookup lookup, String name, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        var targetType = methodType.returnType();
        if (!targetType.isArray()) {
            throw new IllegalArgumentException("Not an array type: "+targetType);
        }
        var enumIshType = targetType.componentType();
        checkEnumIsh(enumIshType);
        var values = lookup.findStatic(enumIshType, "values", MethodType.methodType(enumIshType.arrayType()));
        return new ConstantCallSite(values.asType(methodType));
    }

    private EnumUtils() {}
}
