package test;

import dev.lukebemish.polymorphicsignatures.utilities.EnumUtils;

import java.util.Arrays;

import static dev.lukebemish.polymorphicsignatures.utilities.CollectionUtils.*;
import static dev.lukebemish.polymorphicsignatures.utilities.TypeUtils.*;

public class TestUtilities {
    enum TestEnum {
        A, B, C
    }

    // Doesn't _quite_ suite the contract, but... ehh
    // Fine for testing
    @EnumUtils.EnumIsh
    record TestEnumIsh(String name, int ordinal) {
        private static final TestEnumIsh[] VALUES = new TestEnumIsh[] {new TestEnumIsh("A", 0), new TestEnumIsh("B", 1)};

        public static TestEnumIsh[] values() {
            return Arrays.copyOf(VALUES, VALUES.length);
        }

        public static TestEnumIsh valueOf(String name) {
            return switch (name) {
                case "A" -> VALUES[0];
                case "B" -> VALUES[1];
                default -> throw new IllegalArgumentException(name);
            };
        }
    }

    static void main() {
        String[] stringArray = array(5);
        System.out.println(Arrays.toString(stringArray));
        System.out.println((int) defaultValue());
        System.out.println((int) typeMax());
        System.out.println((float) typeMax());
        System.out.println((Float) typeMin());
        System.out.println((Double) typeNaN());
        reportParameterType(1, System.out::println);
        reportParameterType("abcd", System.out::println);
        reportReturnType("abcd", System.out::println);
        defaultValue();

        TestEnumIsh[] values = EnumUtils.values();
        TestEnumIsh value = EnumUtils.valueOf("A");
        System.out.println(Arrays.toString(values));
        System.out.println(value);
    }
}
