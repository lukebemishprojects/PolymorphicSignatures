package dev.lukebemish.polymorphicsignatures;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({java.lang.annotation.ElementType.METHOD})
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface PolymorphicSignature {
    String value();
    Class<?> clazz() default Self.class;
    
    final class Self {
        private Self() {}
    }
}
