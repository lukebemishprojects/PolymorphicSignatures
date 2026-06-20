package test;

import dev.lukebemish.polymorphicsignatures.Bootstrap;
import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class TestBootstrap {
    static void main() {
        System.out.println("@Receiver: "+TestBootstrapMethods.receiver());
        System.out.println("@Receiver: "+TestBootstrapMethods.receiverMethod());
        System.out.println("@Caller: "+TestBootstrapMethods.caller());
        System.out.println("@Caller: "+TestBootstrapMethods.callerMethod());
    }
}

class TestBootstrapMethods {
    @PolymorphicSignature(value = "receiver", clazz = HasMetafactory.class)
    public native static Class<?> receiver();

    @PolymorphicSignature(value = "receiverMethod", clazz = HasMetafactory.class)
    public native static Method receiverMethod();

    @PolymorphicSignature(value = "caller", clazz = HasMetafactory.class)
    public native static Class<?> caller();

    @PolymorphicSignature(value = "callerMethod", clazz = HasMetafactory.class)
    public native static Method callerMethod();
}

class HasMetafactory {
    public static CallSite receiver(MethodHandles.Lookup lookup, String name, MethodType methodType, @Bootstrap.Receiver Class<?> clazz) {
        return new ConstantCallSite(MethodHandles.constant(Class.class, clazz).asType(methodType));
    }

    public static CallSite receiverMethod(MethodHandles.Lookup lookup, String name, MethodType methodType, @Bootstrap.Receiver Method method) {
        return new ConstantCallSite(MethodHandles.constant(Method.class, method).asType(methodType));
    }

    public static CallSite caller(MethodHandles.Lookup lookup, String name, MethodType methodType, @Bootstrap.Caller Class<?> clazz) {
        return new ConstantCallSite(MethodHandles.constant(Class.class, clazz).asType(methodType));
    }

    public static CallSite callerMethod(MethodHandles.Lookup lookup, String name, MethodType methodType, @Bootstrap.Caller Method method) {
        return new ConstantCallSite(MethodHandles.constant(Method.class, method).asType(methodType));
    }
}
