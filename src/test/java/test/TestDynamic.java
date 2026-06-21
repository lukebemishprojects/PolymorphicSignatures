package test;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import dev.lukebemish.polymorphicsignatures.utilities.Dynamic;

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
    }

    static void dispatch(Gizmo left, Gizmo right) {
        doSomething(left, right);
    }
}
