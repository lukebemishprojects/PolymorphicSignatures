package dev.lukebemish.polymorphicsignatures.utilities;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class CollectionUtils {
    /// {@return an array of the compile-inferred type of the provided size}
    /// This method is signature-polymorphic by its return type; the return type is determined at compile time and used
    /// to produce the proper type array constructor:
    /// ```java
    /// int[] intArray = array(5);
    /// String[] stringArray = array(10);
    /// ```
    /// This is especially useful to create arrays of parameterized types without warnings, though this is of course
    /// less safe than a normal array due to the possibility of unchecked array stores:
    /// ```java
    /// List<String>[] strings = array(10);
    /// ```
    /// @param size the size of the array to create
    /// @param <A> the type of the array to create; will be inferred at compile time.
    /// @throws IllegalArgumentException if the inferred type is not an array type or assignable from `Object[]`
    @PolymorphicSignature("$array")
    public native static <A extends Cloneable & Serializable> A array(int size);

    private CollectionUtils() {}

    @ApiStatus.Internal
    public static CallSite $array(MethodHandles.Lookup lookup, String name, MethodType type) {
        if (type.returnType().isArray()) {
            return new ConstantCallSite(MethodHandles.arrayConstructor(type.returnType()));
        } else if (type.returnType().isAssignableFrom(Object[].class)) {
            return new ConstantCallSite(MethodHandles.arrayConstructor(Object[].class).asType(type));
        }
        return new ConstantCallSite(
            MethodHandles.dropArguments(
                MethodHandles.throwException(type.returnType(), IllegalArgumentException.class)
                    .bindTo(new IllegalArgumentException(String.format("Type '%s' is not an array type", type.returnType()))),
                0, int.class
            )
        );
    }
}
