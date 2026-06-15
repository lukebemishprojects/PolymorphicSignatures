package test;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;

public class TestPolymorphic {
    @PolymorphicSignature("defaultValueMetafactory")
    public static <T> T defaultValue() {
        throw new AssertionError();
    }

    @PolymorphicSignature("argClassMetafactory")
    public static <T> Class<? super T> argClass(T value) {
        throw new AssertionError();
    }

    public static CallSite defaultValueMetafactory(MethodHandles.Lookup lookup, String name, MethodType type) {
        return new ConstantCallSite(MethodHandles.constant(
                type.returnType(),
                Array.get(Array.newInstance(type.returnType(), 1), 0)
        ));
    }

    public static CallSite argClassMetafactory(MethodHandles.Lookup lookup, String name, MethodType type) {
        return new ConstantCallSite(MethodHandles.dropArguments(MethodHandles.constant(
                Class.class,
                type.parameterType(0)
        ), 0, type.parameterType(0)));
    }

    static void main() {
        int zero1 = defaultValue();
        System.out.println("int: "+zero1);
        float zero2 = defaultValue();
        System.out.println("float: "+zero2);
        Object zero3 = defaultValue();
        System.out.println("Object: "+zero3);
        String zero4 = defaultValue();
        System.out.println("String: "+zero4);
        System.out.println("int class: "+argClass(1));
        System.out.println("long class: "+argClass(1L));
        System.out.println("String class: "+argClass("test"));
        System.out.println("Runnable class: "+argClass((Runnable) () -> {}));
    }
}
