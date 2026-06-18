package test;

import java.util.Arrays;

import static dev.lukebemish.polymorphicsignatures.utilities.CollectionUtils.*;

public class TestCollectionUtilities {
    static void main() {
        String[] stringArray = array(5);
        System.out.println(Arrays.toString(stringArray));
    }
}
