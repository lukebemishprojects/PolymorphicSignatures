package dev.lukebemish.polymorphicsignatures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PolymorphicSignature {
    String value();
    Class<?> clazz() default Self.class;

    final class Self {
        private Self() {}
    }
}
