package dev.lukebemish.polymorphicsignatures.impl;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Objects;

/**
 * The most specific known value on the stack, ignoring primitive boxing.
 * Primitive-boxed types link to the boxing instruction.
 */
class PolymorphicValue implements Value {
    public static final PolymorphicValue UNINITIALIZED_VALUE = new PolymorphicValue(null);

    public static final PolymorphicValue BYTE_VALUE = new PolymorphicValue(Type.BYTE_TYPE);
    public static final PolymorphicValue BOOLEAN_VALUE = new PolymorphicValue(Type.BOOLEAN_TYPE);
    public static final PolymorphicValue CHAR_VALUE = new PolymorphicValue(Type.CHAR_TYPE);
    public static final PolymorphicValue SHORT_VALUE = new PolymorphicValue(Type.SHORT_TYPE);
    public static final PolymorphicValue INT_VALUE = new PolymorphicValue(Type.INT_TYPE);

    public static final PolymorphicValue FLOAT_VALUE = new PolymorphicValue(Type.FLOAT_TYPE);

    public static final PolymorphicValue LONG_VALUE = new PolymorphicValue(Type.LONG_TYPE);

    public static final PolymorphicValue DOUBLE_VALUE = new PolymorphicValue(Type.DOUBLE_TYPE);

    public static final PolymorphicValue RETURNADDRESS_VALUE = new PolymorphicValue(Type.VOID_TYPE);
    
    private final @Nullable Type type;
    private final @Nullable AbstractInsnNode boxing;

    public PolymorphicValue(@Nullable Type type, @Nullable AbstractInsnNode boxing) {
        this.type = type;
        this.boxing = boxing;
    }

    public PolymorphicValue(@Nullable Type type) {
        this(type, null);
    }

    @Override
    public int getSize() {
        return type != null ? type.getSize() : 1;
    }
    
    public @Nullable Type getType() {
        return type;
    }
    
    public @Nullable AbstractInsnNode getBoxing() {
        return boxing;
    }
    
    public PolymorphicValue withoutBoxing() {
        return new PolymorphicValue(type);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PolymorphicValue that)) return false;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }
    
    public static @Nullable BasicValue basic(@Nullable PolymorphicValue value) {
        if (value == null) return null;
        var type = value.type;
        if (type == null) {
            return BasicValue.UNINITIALIZED_VALUE;
        }
        return switch (type.getSort()) {
            case Type.VOID -> null;
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> BasicValue.INT_VALUE;
            case Type.FLOAT -> BasicValue.FLOAT_VALUE;
            case Type.LONG -> BasicValue.LONG_VALUE;
            case Type.DOUBLE -> BasicValue.DOUBLE_VALUE;
            case Type.ARRAY, Type.OBJECT -> BasicValue.REFERENCE_VALUE;
            default -> throw new AssertionError();
        };
    }

    @Override
    public String toString() {
        return "PolymorphicValue{" +
                "type=" + type +
                ", boxing=" + boxing +
                '}';
    }
}
