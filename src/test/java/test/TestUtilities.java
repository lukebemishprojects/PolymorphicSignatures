package test;

import dev.lukebemish.polymorphicsignatures.utilities.TypeUtils;

import java.util.Arrays;

import static dev.lukebemish.polymorphicsignatures.utilities.CollectionUtils.*;

public class TestUtilities {
    static void main() {
        String[] stringArray = array(5);
        System.out.println(Arrays.toString(stringArray));
        System.out.println((int) TypeUtils.defaultValue());
        System.out.println((int) TypeUtils.typeMax());
        System.out.println((float) TypeUtils.typeMax());
        System.out.println((Float) TypeUtils.typeMin());
        System.out.println((Double) TypeUtils.typeNaN());
        TypeUtils.reportParameterType(1, System.out::println);
        TypeUtils.reportParameterType("abcd", System.out::println);
        TypeUtils.reportReturnType("abcd", System.out::println);
        TypeUtils.defaultValue();
    }
}
