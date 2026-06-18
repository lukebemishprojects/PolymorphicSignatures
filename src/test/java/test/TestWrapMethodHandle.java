package test;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.io.PrintStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestWrapMethodHandle {
    @PolymorphicSignature("metafactory")
    static Object invoke(MethodHandle handle, Object... args) {
        throw new AssertionError();
    }

    static CallSite metafactory(MethodHandles.Lookup lookup, String name, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        return new ConstantCallSite(lookup.findVirtual(MethodHandle.class, "invoke", methodType.dropParameterTypes(0, 1)));
    }

    private static final MethodHandle HANDLE;

    static {
        try {
            HANDLE = MethodHandles.lookup().findStaticGetter(System.class, "out", PrintStream.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static void main() {
        ((PrintStream) invoke(HANDLE)).println("Test");
    }
}
