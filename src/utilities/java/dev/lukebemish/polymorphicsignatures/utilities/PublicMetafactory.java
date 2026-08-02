package dev.lukebemish.polymorphicsignatures.utilities;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Methods annotated with this are metafactories meant to be used as public API, but only by being referenced as the
/// metafactory of a {@link dev.lukebemish.polymorphicsignatures.PolymorphicSignature} method. The behavior of any use
/// not generated this way is undefined; however, they may be used by consumer methods (unlike {@link ImplementationMetafactory},
/// which is just meant for use by methods provided here). The metafactories are considered public binary API in the
/// sense that their signature (and behavior for generated {@code INVOKEDYNAMIC} through a polymorphic call) will be stable.
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@interface PublicMetafactory {
}
