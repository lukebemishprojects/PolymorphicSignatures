package dev.lukebemish.polymorphicsignatures.utilities;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import org.jetbrains.annotations.ApiStatus;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

public class TypeUtils {
    /// {@return the default value of the output type}
    /// This is the value that a newly-created array of that type would be populated with; it is {@code null} for
    /// reference types and {@code 0} for primitives, or nothing at all for void.
    /// @param <T> the type to return; will be inferred at compile time
    @PolymorphicSignature("$defaultValue")
    public native static <T> T defaultValue();

    /// {@return the maximum value of the output type}
    /// @param <T> the type to return; will be inferred at compile time
    /// @throws UnsupportedOperationException if the type does not have a known or accessible maximum value
    @PolymorphicSignature("$typeBound")
    public native static <T extends Number> T typeMax();
    /// {@return the minimum value of the output type}
    /// @param <T> the type to return; will be inferred at compile time
    /// @throws UnsupportedOperationException if the type does not have a known or accessible minimum value
    @PolymorphicSignature("$typeBound")
    public native static <T extends Number> T typeMin();

    /// {@return a value of the output type holding a Not-a-Number (NaN) value}
    /// @param <T> the type to return; will be inferred at compile time
    /// @throws UnsupportedOperationException if the type does not have a known or accessible NaN value
    @PolymorphicSignature("$typeNaN")
    public native static <T extends Number> T typeNaN();

    /// {@return the input value unmodified}
    /// This method returns the input value unmodified, but passes to the supplied consumer the compile-time inferred
    /// type of the parameter value. This is useful for debugging {@link PolymorphicSignature} methods.
    /// @param <T> the type of the value; will be inferred at compile time
    /// @param value the value to pass unmodified and uninspected
    /// @param consumer the consumer to feed the inferred type to
    /// @see #reportReturnType(Object, Consumer)
    @PolymorphicSignature("$reportType")
    public native static <T> T reportParameterType(T value, Consumer<? super Class<? extends T>> consumer);

    /// {@return the input value unmodified}
    /// This method returns the input value unmodified, but passes to the supplied consumer the compile-time inferred
    /// type of the method return. This is useful for debugging {@link PolymorphicSignature} methods.
    /// @param <T> the type of the value; will be inferred at compile time
    /// @param value the value to pass unmodified and uninspected
    /// @param consumer the consumer to feed the inferred type to
    /// @see #reportParameterType(Object, Consumer)
    @PolymorphicSignature("$reportType")
    public native static <T> T reportReturnType(T value, Consumer<? super Class<? extends T>> consumer);

    @ApiStatus.Internal
    public static CallSite $defaultValue(MethodHandles.Lookup lookup, String name, MethodType type) {
        return new ConstantCallSite(MethodHandles.empty(type));
    }

    @ApiStatus.Internal
    public static CallSite $typeBound(MethodHandles.Lookup lookup, String name, MethodType type) {
        var isMin = name.equals("typeMin");
        MethodHandle handle;
        if (type.returnType() == void.class) {
            handle = MethodHandles.empty(type);
        } else if (type.returnType() == byte.class || type.returnType() == Byte.class) {
            handle = MethodHandles.constant(type.returnType(), isMin ? Byte.MIN_VALUE : Byte.MAX_VALUE);
        } else if (type.returnType() == short.class || type.returnType() == Short.class) {
            handle = MethodHandles.constant(type.returnType(), isMin ? Short.MIN_VALUE : Short.MAX_VALUE);
        } else if (type.returnType() == int.class || type.returnType() == Integer.class) {
            handle = MethodHandles.constant(type.returnType(), isMin ? Integer.MIN_VALUE : Integer.MAX_VALUE);
        } else if (type.returnType() == long.class || type.returnType() == Long.class) {
            handle = MethodHandles.constant(type.returnType(), isMin ? Long.MIN_VALUE : Long.MAX_VALUE);
        } else if (type.returnType() == float.class || type.returnType() == Float.class) {
            handle = MethodHandles.constant(type.returnType(), isMin ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
        } else if (type.returnType() == double.class || type.returnType() == Double.class) {
            handle = MethodHandles.constant(type.returnType(), isMin ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        } else {
            try {
                handle = lookup.findStaticGetter(type.returnType(), isMin ? "NEGATIVE_INFINITY" : "POSITIVE_INFINITY", type.returnType());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                try {
                    handle = lookup.findStaticGetter(type.returnType(), isMin ? "MIN_VALUE" : "MAX_VALUE", type.returnType());
                } catch (NoSuchFieldException | IllegalAccessException ex) {
                    handle = MethodHandles.throwException(type.returnType(), UnsupportedOperationException.class)
                        .bindTo(new UnsupportedOperationException(String.format("No type %s known or accessible for type '%s'", isMin ? "min" : "max", type.returnType())));
                }
            }
        }
        return new ConstantCallSite(handle);
    }

    @ApiStatus.Internal
    public static CallSite $typeNaN(MethodHandles.Lookup lookup, String name, MethodType type) {
        MethodHandle handle;
        if (type.returnType() == void.class) {
            handle = MethodHandles.empty(type);
        } else if (type.returnType() == float.class || type.returnType() == Float.class) {
            handle = MethodHandles.constant(type.returnType(), Float.NaN);
        } else if (type.returnType() == double.class || type.returnType() == Double.class) {
            handle = MethodHandles.constant(type.returnType(), Double.NaN);
        } else {
            try {
                handle = lookup.findStaticGetter(type.returnType(), "NaN", type.returnType());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                handle = MethodHandles.throwException(type.returnType(), UnsupportedOperationException.class)
                    .bindTo(new UnsupportedOperationException(String.format("No type NaN known or accessible for type '%s'", type.returnType())));
            }
        }
        return new ConstantCallSite(handle);
    }

    @ApiStatus.Internal
    public static CallSite $reportType(MethodHandles.Lookup lookup, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        var valueType = name.equals("reportParameterType") ? type.parameterType(0) : type.returnType();
        var consume = MethodHandles.permuteArguments(
            MethodHandles.lookup().findVirtual(Consumer.class, "accept", MethodType.methodType(void.class, Object.class)),
            MethodType.methodType(void.class, Object.class, Consumer.class),
            1, 0
        ).bindTo(valueType);
        return new ConstantCallSite(MethodHandles.collectArguments(MethodHandles.identity(type.parameterType(0)), 1, consume).asType(type));
    }
}
