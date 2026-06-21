package test;

import java.util.Arrays;

import static dev.lukebemish.polymorphicsignatures.utilities.CollectionUtils.*;
import static dev.lukebemish.polymorphicsignatures.utilities.TypeUtils.*;

public class TestUtilities {
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
    }
}
