// Large parts of this file adapted from BasicInterpreter.java in asm-analysis
// That class is distributed with the following license header, preserved here:
//
// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.

package dev.lukebemish.polymorphicsignatures.impl;

import dev.lukebemish.javacpostprocessor.PostProcessor;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class PolymorphicInterpreter extends Interpreter<PolymorphicValue> implements Opcodes {
    private final PostProcessor.Context.CommonSuperClassFinder commonSuperClassFinder;

    PolymorphicInterpreter(int api, PostProcessor.Context.CommonSuperClassFinder commonSuperClassFinder) {
        super(api);
        this.commonSuperClassFinder = commonSuperClassFinder;
    }

    @Override
    public @Nullable PolymorphicValue newValue(final @Nullable Type type) {
        if (type == null) {
            return PolymorphicValue.UNINITIALIZED_VALUE;
        }
        return switch (type.getSort()) {
            case Type.VOID -> null;
            case Type.BOOLEAN -> PolymorphicValue.BOOLEAN_VALUE;
            case Type.CHAR -> PolymorphicValue.CHAR_VALUE;
            case Type.BYTE -> PolymorphicValue.BYTE_VALUE;
            case Type.SHORT -> PolymorphicValue.SHORT_VALUE;
            case Type.INT -> PolymorphicValue.INT_VALUE;
            case Type.FLOAT -> PolymorphicValue.FLOAT_VALUE;
            case Type.LONG -> PolymorphicValue.LONG_VALUE;
            case Type.DOUBLE -> PolymorphicValue.DOUBLE_VALUE;
            case Type.ARRAY, Type.OBJECT -> new PolymorphicValue(type);
            default -> throw new AssertionError();
        };
    }

    @Override
    public @Nullable PolymorphicValue newOperation(final AbstractInsnNode insn) throws AnalyzerException {
        switch (insn.getOpcode()) {
            case ACONST_NULL:
                return newValue(Type.getType(Object.class));
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                return PolymorphicValue.INT_VALUE;
            case LCONST_0:
            case LCONST_1:
                return PolymorphicValue.LONG_VALUE;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                return PolymorphicValue.FLOAT_VALUE;
            case DCONST_0:
            case DCONST_1:
                return PolymorphicValue.DOUBLE_VALUE;
            case BIPUSH:
            case SIPUSH:
                return PolymorphicValue.INT_VALUE;
            case LDC:
                Object value = ((LdcInsnNode) insn).cst;
                return switch (value) {
                    case Integer i -> PolymorphicValue.INT_VALUE;
                    case Float v -> PolymorphicValue.FLOAT_VALUE;
                    case Long l -> PolymorphicValue.LONG_VALUE;
                    case Double v -> PolymorphicValue.DOUBLE_VALUE;
                    case String s -> newValue(Type.getObjectType("java/lang/String"));
                    case Type type -> {
                        int sort = type.getSort();
                        if (sort == Type.OBJECT || sort == Type.ARRAY) {
                            yield newValue(Type.getObjectType("java/lang/Class"));
                        } else if (sort == Type.METHOD) {
                            yield newValue(Type.getObjectType("java/lang/invoke/MethodType"));
                        } else {
                            throw new AnalyzerException(insn, "Illegal LDC value " + value);
                        }
                    }
                    case Handle handle -> newValue(Type.getObjectType("java/lang/invoke/MethodHandle"));
                    case ConstantDynamic constantDynamic -> newValue(Type.getType(constantDynamic.getDescriptor()));
                    case null, default -> throw new AnalyzerException(insn, "Illegal LDC value " + value);
                };
            case JSR:
                return PolymorphicValue.RETURNADDRESS_VALUE;
            case GETSTATIC:
                return newValue(Type.getType(((FieldInsnNode) insn).desc));
            case NEW:
                return newValue(Type.getObjectType(((TypeInsnNode) insn).desc));
            default:
                throw new AssertionError();
        }
    }

    @Override
    public @Nullable PolymorphicValue copyOperation(final AbstractInsnNode insn, final @Nullable PolymorphicValue value)
            throws AnalyzerException {
        return value;
    }

    @Override
    public @Nullable PolymorphicValue unaryOperation(final AbstractInsnNode insn, final PolymorphicValue value)
            throws AnalyzerException {
        switch (insn.getOpcode()) {
            case INEG:
            case IINC:
                return value;
            case L2I:
            case F2I:
            case D2I:
                return PolymorphicValue.INT_VALUE;
            case I2B:
                return PolymorphicValue.BYTE_VALUE;
            case I2C:
                return PolymorphicValue.CHAR_VALUE;
            case I2S:
                return PolymorphicValue.SHORT_VALUE;
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
                return PolymorphicValue.FLOAT_VALUE;
            case LNEG:
            case I2L:
            case F2L:
            case D2L:
                return PolymorphicValue.LONG_VALUE;
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                return PolymorphicValue.DOUBLE_VALUE;
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case PUTSTATIC:
                return null;
            case GETFIELD:
                return newValue(Type.getType(((FieldInsnNode) insn).desc));
            case NEWARRAY:
                return switch (((IntInsnNode) insn).operand) {
                    case T_BOOLEAN -> newValue(Type.getType("[Z"));
                    case T_CHAR -> newValue(Type.getType("[C"));
                    case T_BYTE -> newValue(Type.getType("[B"));
                    case T_SHORT -> newValue(Type.getType("[S"));
                    case T_INT -> newValue(Type.getType("[I"));
                    case T_FLOAT -> newValue(Type.getType("[F"));
                    case T_DOUBLE -> newValue(Type.getType("[D"));
                    case T_LONG -> newValue(Type.getType("[J"));
                    default -> throw new AnalyzerException(insn, "Invalid array type");
                };
            case ANEWARRAY:
                return newValue(Type.getType("[" + Type.getObjectType(((TypeInsnNode) insn).desc)));
            case ARRAYLENGTH:
                return PolymorphicValue.INT_VALUE;
            case ATHROW:
                return null;
            case CHECKCAST:
                return newValue(Type.getObjectType(((TypeInsnNode) insn).desc));
            case INSTANCEOF:
                return PolymorphicValue.BOOLEAN_VALUE;
            case MONITORENTER:
            case MONITOREXIT:
            case IFNULL:
            case IFNONNULL:
                return null;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public @Nullable PolymorphicValue binaryOperation(
            final AbstractInsnNode insn, final @Nullable PolymorphicValue value1, final @Nullable PolymorphicValue value2)
            throws AnalyzerException {
        switch (insn.getOpcode()) {
            case IALOAD:
                return PolymorphicValue.INT_VALUE;
            case BALOAD:
                return PolymorphicValue.BYTE_VALUE;
            case CALOAD:
                return PolymorphicValue.CHAR_VALUE;
            case SALOAD:
                return PolymorphicValue.SHORT_VALUE;
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
                return PolymorphicValue.INT_VALUE;
            case FALOAD:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                return PolymorphicValue.FLOAT_VALUE;
            case LALOAD:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
                return PolymorphicValue.LONG_VALUE;
            case DALOAD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                return PolymorphicValue.DOUBLE_VALUE;
            case AALOAD:
                if (value1 != null && value1.getType() != null && value1.getType().getSort() == Type.ARRAY) {
                    return newValue(value1.getType().getElementType());
                }
                return newValue(Type.getType(Object.class));
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                return PolymorphicValue.BOOLEAN_VALUE;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case PUTFIELD:
                return null;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public @Nullable PolymorphicValue ternaryOperation(
            final AbstractInsnNode insn,
            final @Nullable PolymorphicValue value1,
            final @Nullable PolymorphicValue value2,
            final @Nullable PolymorphicValue value3)
            throws AnalyzerException {
        return null;
    }

    private final Map<String, Type> BOXING = Map.of(
            Type.getType(Boolean.class).getInternalName(), Type.getType(boolean.class),
            Type.getType(Byte.class).getInternalName(), Type.getType(byte.class),
            Type.getType(Character.class).getInternalName(), Type.getType(char.class),
            Type.getType(Short.class).getInternalName(), Type.getType(short.class),
            Type.getType(Integer.class).getInternalName(), Type.getType(int.class),
            Type.getType(Long.class).getInternalName(), Type.getType(long.class),
            Type.getType(Float.class).getInternalName(), Type.getType(float.class),
            Type.getType(Double.class).getInternalName(), Type.getType(double.class)
    );

    @Override
    public @Nullable PolymorphicValue naryOperation(
            final AbstractInsnNode insn, final List<? extends @Nullable PolymorphicValue> values)
            throws AnalyzerException {
        int opcode = insn.getOpcode();
        if (opcode == MULTIANEWARRAY) {
            return newValue(Type.getType(((MultiANewArrayInsnNode) insn).desc));
        } else if (opcode == INVOKEDYNAMIC) {
            return newValue(Type.getReturnType(((InvokeDynamicInsnNode) insn).desc));
        } else {
            var methodInsn = (MethodInsnNode) insn;
            if (methodInsn.getOpcode() == INVOKESTATIC && BOXING.containsKey(methodInsn.owner) && Arrays.equals(
                    Type.getMethodType(methodInsn.desc).getArgumentTypes(),
                    new Type[] {BOXING.get(methodInsn.owner)}
            )) {
                return new PolymorphicValue(
                    BOXING.get(methodInsn.owner),
                        insn
                );
            }
            return newValue(Type.getReturnType(((MethodInsnNode) insn).desc));
        }
    }

    @Override
    public void returnOperation(
            final AbstractInsnNode insn, final @Nullable PolymorphicValue value, final @Nullable PolymorphicValue expected)
            throws AnalyzerException {
        // Nothing to do.
    }

    @Override
    public @Nullable PolymorphicValue merge(final @Nullable PolymorphicValue value1, final @Nullable PolymorphicValue value2) {
        if (!Objects.equals(value1, value2)) {
            var basic1 = PolymorphicValue.basic(value1);
            var basic2 =  PolymorphicValue.basic(value2);
            if (Objects.equals(basic1, basic2)) {
                if (basic1 != null && basic1.isReference()) {
                    Objects.requireNonNull(value1.getType());
                    Objects.requireNonNull(value2.getType());
                    var commonSuperType = commonSuperClassFinder.findCommonSuperClass(value1.getType().getInternalName(), value2.getType().getInternalName());
                    if (commonSuperType != null) {
                        return new PolymorphicValue(Type.getObjectType(commonSuperType));
                    }
                } else {
                    return basic1 == null ? null : new PolymorphicValue(basic1.getType());
                }
            }
            return PolymorphicValue.UNINITIALIZED_VALUE;
        }
        return value1 != null ? value1.withoutBoxing() : null;
    }
}
