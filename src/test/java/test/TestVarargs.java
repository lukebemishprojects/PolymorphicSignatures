package test;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestVarargs {
    @PolymorphicSignature("metafactory")
    static MethodType primitives(int... ints) {
        throw new AssertionError();
    }
    @PolymorphicSignature("metafactory")
    static MethodType objects(Object... objects) {
        throw new AssertionError();
    }
    @PolymorphicSignature("metafactory")
    static MethodType longprimitives(double... doubles) {
        throw new AssertionError();
    }

    static CallSite metafactory(MethodHandles.Lookup lookup, String name, MethodType type) {
        return new ConstantCallSite(MethodHandles.dropArguments(
            MethodHandles.constant(MethodType.class, type),
            0, type.parameterArray()
        ));
    }

    static void main(String... args) {
        System.out.println(primitives(1, 2, 3));
        System.out.println(objects("a", "b", "c"));
        System.out.println(longprimitives(1, 2, 3));
    }
}
