package test;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import dev.lukebemish.polymorphicsignatures.utilities.Dynamic;

import java.lang.reflect.AccessFlag;
import java.time.chrono.Chronology;
import java.util.regex.Pattern;

public class TestDynamic {
    sealed interface Gizmo permits Foo, Bar {}
    record Foo() implements Gizmo {}
    record Bar() implements Gizmo {}

    @PolymorphicSignature(value = "dynamic", clazz = Dynamic.class)
    static native void doSomething(Gizmo left, Gizmo right);

    private static void doSomething(Foo left, Foo right) {
        System.out.println("(Foo, Foo)");
    }

    private static void doSomething(Foo left, Bar right) {
        System.out.println("(Foo, Bar)");
    }

    private static void doSomething(Bar left, Bar right) {
        System.out.println("(Bar, Bar)");
    }

    private static void doSomething(Gizmo left, Foo right) {
        System.out.println("(Gizmo, Foo)");
    }

    @PolymorphicSignature(value = "dynamic", clazz = Dynamic.class)
    static native void testPrimitives(Object object);

    private static void testPrimitives(Number object) {
        System.out.println("(Number)");
    }

    private static void testPrimitives(Double object) {
        System.out.println("(Double)");
    }

    private static void testPrimitives(float object) {
        System.out.println("(float)");
    }

    @PolymorphicSignature(value = "dynamic", clazz = Dynamic.class)
    static native void testArrays(Object object);

    private static void testArrays(Object[] object) {
        System.out.println("(Object[])");
    }

    private static void testArrays(Comparable<?>[] object) {
        System.out.println("(Comparable[])");
    }

    private static void testArrays(String[] object) {
        System.out.println("(String[])");
    }

    private static void testArrays(int[] object) {
        System.out.println("(int[])");
    }

    static void main() {
        Gizmo foo = new Foo();
        Gizmo bar = new Bar();
        System.out.println("Specialized:");
        doSomething(foo, foo);
        doSomething(foo, bar);
        doSomething(bar, foo);
        doSomething(bar, bar);
        System.out.println("Dispatch:");
        dispatch(foo, foo);
        dispatch(foo, bar);
        dispatch(bar, foo);
        dispatch(bar, bar);
        System.out.println("Specialized (Primitives):");
        testPrimitives((Object) 1L); // (Number)
        testPrimitives((Object) 1F); // (float)
        testPrimitives((Object) 1D); // (Double)
        System.out.println("Dispatch (Primitives):");
        dispatchPrimitive(1L); // (Number)
        dispatchPrimitive(1F); // (float)
        dispatchPrimitive(1D); // (Double)
        System.out.println("Specialized (Arrays):");
        testArrays((Object) new String[0]); // String[]
        testArrays((Object) new int[0]); // int[]
        testArrays((Object) new AccessFlag[0]); // Comparable[]
        testArrays((Object) new Chronology[0]); // Comparable[]
        testArrays((Object) new Pattern[0]); // Object[]
        System.out.println("Dispatch (Arrays):");
        dispatchArray(new String[0]); // String[]
        dispatchArray(new int[0]); // int[]
        dispatchArray(new AccessFlag[0]); // Comparable[]
        dispatchArray(new Chronology[0]); // Comparable[]
        dispatchArray(new Pattern[0]); // Object[]
    }

    static void dispatch(Gizmo left, Gizmo right) {
        doSomething(left, right);
    }

    static void dispatchPrimitive(Object object) {
        testPrimitives(object);
    }

    static void dispatchArray(Object object) {
        testArrays(object);
    }
}
