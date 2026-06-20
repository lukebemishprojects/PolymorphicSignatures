package dev.lukebemish.polymorphicsignatures.impl;

import com.google.auto.service.AutoService;
import dev.lukebemish.bytecodebuilder.BackendASM;
import dev.lukebemish.bytecodebuilder.Constants;
import dev.lukebemish.javacpostprocessor.PostProcessor;
import dev.lukebemish.polymorphicsignatures.Bootstrap;
import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@AutoService(PostProcessor.class)
public final class PolymorphicSignaturesPlugin implements PostProcessor {
    private @Nullable Elements elements;
    private @Nullable Types types;
    private Context.@Nullable CommonSuperClassFinder commonSuperClassFinder;
    private Context.@Nullable BinaryBridge binaryBridge;

    private record AnnotationInfo(
            TypeElement annotation,
            ExecutableElement value,
            ExecutableElement clazz,
            TypeElement self
    ) {
        AnnotationInfo(Elements elements) {
            var annotation = Objects.requireNonNull(elements.getTypeElement(PolymorphicSignature.class.getCanonicalName()), "@PolymorphicSignature not on classpath");
            var value = annotation.getEnclosedElements().stream()
                    .flatMap(it -> it instanceof ExecutableElement executableElement && executableElement.getSimpleName().contentEquals("value") ? Stream.of(executableElement) : Stream.empty())
                    .findFirst().orElseThrow(() -> new IllegalStateException("@PolymorphicSignature has no value() method"));
            var clazz = annotation.getEnclosedElements().stream()
                    .flatMap(it -> it instanceof ExecutableElement executableElement && executableElement.getSimpleName().contentEquals("clazz") ? Stream.of(executableElement) : Stream.empty())
                    .findFirst().orElseThrow(() -> new IllegalStateException("@PolymorphicSignature has no clazz() method"));
            var self = Objects.requireNonNull(elements.getTypeElement(PolymorphicSignature.Self.class.getCanonicalName()), "PolymorphicSignature.Self not on classpath");
            this(annotation, value, clazz, self);
        }
    }

    @Override
    public void context(Context context) {
        elements = context.task().getElements();
        types = context.task().getTypes();
        commonSuperClassFinder = context.commonSuperClassFinder();
        binaryBridge = context.binaryBridge();
    }

    @Override
    public String name() {
        return "dev.lukebemish.polymorphic-signatures";
    }

    @Override
    public ClassVisitor visit(ClassVisitor next, String binaryName, JavaFileManager fileManager, JavaFileManager.Location location) {
        var elements = Objects.requireNonNull(this.elements);
        var types =  Objects.requireNonNull(this.types);
        var binaryBridge = Objects.requireNonNull(this.binaryBridge);
        var descriptors = new DescriptorTypeVisitor(elements, types);
        var annotationInfo = new AnnotationInfo(elements);
        return new ClassVisitor(Opcodes.ASM9, next) {
            @Override
            public MethodVisitor visitMethod(int access, String ownerName, String ownerDescriptor, String signature, String[] exceptions) {
                var node = new MethodNode(access, ownerName, ownerDescriptor, signature, exceptions);
                Supplier<MethodVisitor> superVisitor = () -> super.visitMethod(node.access, ownerName, ownerDescriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, node) {
                    @Override
                    public void visitEnd() {
                        node.visitEnd();
                        if (node.visibleAnnotations != null) {
                            for (var annotation : node.visibleAnnotations) {
                                if (annotation.desc.equals(PolymorphicSignature.class.descriptorString())) {
                                    // Replace method body with erroring one that reports lack of compilation with this post-processor
                                    var insnList = new InsnList();
                                    insnList.add(new TypeInsnNode(Opcodes.NEW, Type.getInternalName(AssertionError.class)));
                                    insnList.add(new InsnNode(Opcodes.DUP));
                                    insnList.add(new LdcInsnNode(
                                        "This method should not be called directly; consuming code should be built with the polymorphic-signatures compiler plugin so that the relevant metafactory is used." +
                                            ((node.access & Opcodes.ACC_VARARGS) == 0 ? "" : " This method may not be invoked directly with an array as its varargs argument.")
                                    ));
                                    insnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, Type.getInternalName(AssertionError.class), "<init>", "(Ljava/lang/Object;)V", false));
                                    insnList.add(new InsnNode(Opcodes.ATHROW));
                                    node.instructions = insnList;
                                    node.access &= ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);
                                }
                            }
                        }
                        // Do processing on node here
                        for (int idx = 0; idx < node.instructions.size(); idx++) {
                            var insn = node.instructions.get(idx);
                            if (insn instanceof MethodInsnNode methodInsn) {
                                ExecutableElement methodElement = findMethodMatching(methodInsn, binaryBridge, descriptors);
                                if (methodElement != null) {
                                    if (methodElement.getAnnotation(PolymorphicSignature.class) != null) {
                                        AnnotationMirror mirror = null;
                                        for (var aMirror : methodElement.getAnnotationMirrors()) {
                                            if (types.isSameType(aMirror.getAnnotationType(), annotationInfo.annotation().asType())) {
                                                mirror = aMirror;
                                            }
                                        }

                                        var descriptor = MethodTypeDesc.ofDescriptor(methodInsn.desc);

                                        // if non-static, prepend the receiver type
                                        if (methodInsn.getOpcode() != Opcodes.INVOKESTATIC) {
                                            descriptor = descriptor.insertParameterTypes(0, ClassDesc.ofInternalName(methodInsn.owner));
                                        }

                                        descriptor = findActualDescriptor(ownerName, node, methodInsn, descriptor, methodElement.isVarArgs());
                                        if (descriptor == null) {
                                            continue;
                                        }

                                        var implName = (String) Objects.requireNonNull(mirror).getElementValues().get(annotationInfo.value).getValue();
                                        var implClazz = mirror.getElementValues().get(annotationInfo.clazz);
                                        var implClazzType = implClazz == null ? annotationInfo.self.asType() : (TypeMirror) implClazz.getValue();
                                        if (!(implClazzType instanceof DeclaredType declaredImplClazzType)) {
                                            throw new IllegalArgumentException(String.format("clazz of @PolymorphicSignature must be a declared type, not '%s'", implClazzType));
                                        }
                                        var implReceiver = types.isSameType(types.erasure(implClazzType), annotationInfo.self.asType()) ? methodInsn.owner : elements.getBinaryName((TypeElement) declaredImplClazzType.asElement()).toString().replace('.', '/');

                                        List<Integer> receiverArgIdxs = new ArrayList<>();
                                        List<Integer> callerArgIdxs = new ArrayList<>();

                                        ExecutableElement metafactoryElement = findMethodMatching(
                                            implReceiver,
                                            implName,
                                            desc -> Type.getReturnType(desc).getDescriptor().equals(CallSite.class.descriptorString()) &&
                                                Type.getArgumentCount(desc) >= 3 &&
                                                Type.getArgumentTypes(desc)[0].getDescriptor().equals(MethodHandles.Lookup.class.descriptorString()) &&
                                                Type.getArgumentTypes(desc)[1].getDescriptor().equals(String.class.descriptorString()) &&
                                                Type.getArgumentTypes(desc)[2].getDescriptor().equals(MethodType.class.descriptorString()),
                                            binaryBridge,
                                            descriptors
                                        );
                                        if (metafactoryElement == null) {
                                            throw new IllegalArgumentException(String.format("Could not find metafactory %s in %s", implName, implReceiver));
                                        }
                                        for (int i = 0; i < metafactoryElement.getParameters().size(); i++) {
                                            var param = metafactoryElement.getParameters().get(i);
                                            String used = null;
                                            if (param.getAnnotation(Bootstrap.Receiver.class) != null) {
                                                used = "@Receiver";
                                                var desc = descriptors.descriptor(param.asType());
                                                if (!desc.equals(Class.class.descriptorString()) && !desc.equals(Method.class.descriptorString())) {
                                                    throw new IllegalArgumentException(String.format("Found @Receiver on non-Class or Method argument of metafactory %s in %s", implName, implReceiver));
                                                } else {
                                                    receiverArgIdxs.add(i - 3);
                                                }
                                            }
                                            if (param.getAnnotation(Bootstrap.Caller.class) != null) {
                                                if (used != null) {
                                                    throw new IllegalArgumentException(String.format("Bootstrap parameter cannot have both @Caller and %s", used));
                                                }
                                                used = "@Caller";
                                                var desc = descriptors.descriptor(param.asType());
                                                if (!desc.equals(Class.class.descriptorString()) && !desc.equals(Method.class.descriptorString())) {
                                                    throw new IllegalArgumentException(String.format("Found @Caller on non-Class or Method argument of metafactory %s in %s", implName, implReceiver));
                                                } else {
                                                    callerArgIdxs.add(i - 3);
                                                }
                                            }
                                        }

                                        var isInterface = types.isSameType(types.erasure(implClazzType), annotationInfo.self.asType()) ? methodInsn.itf : declaredImplClazzType.asElement().getKind() == ElementKind.INTERFACE;
                                        idx = node.instructions.indexOf(insn);
                                        Object[] args = new Object[metafactoryElement.getParameters().size() - 3];
                                        var metafactoryDescriptor = descriptors.descriptor(metafactoryElement.asType());
                                        var metafactoryParameterTypes = Type.getArgumentTypes(metafactoryDescriptor);

                                        for (int i : receiverArgIdxs) {
                                            var desc = metafactoryParameterTypes[i + 3].getDescriptor();
                                            if (desc.equals(Class.class.descriptorString())) {
                                                args[i] = Type.getObjectType(methodInsn.owner);
                                            } else {
                                                // Method
                                                args[i] = getMethodConstant(methodInsn.desc, methodInsn.owner.replace('/', '.'), methodInsn.name);
                                            }
                                        }
                                        for (int i : callerArgIdxs) {
                                            var desc = metafactoryParameterTypes[i + 3].getDescriptor();
                                            if (desc.equals(Class.class.descriptorString())) {
                                                args[i] = Type.getObjectType(binaryName.replace('.', '/'));
                                            } else {
                                                // Method
                                                args[i] = getMethodConstant(ownerDescriptor, binaryName, ownerName);
                                            }
                                        }
                                        node.instructions.set(insn, new InvokeDynamicInsnNode(
                                                methodInsn.name,
                                                descriptor.descriptorString(),
                                                new Handle(
                                                        Opcodes.H_INVOKESTATIC,
                                                        implReceiver,
                                                        implName,
                                                        metafactoryDescriptor,
                                                        isInterface
                                                ),
                                            args
                                        ));
                                    }
                                }
                            }
                        }
                        node.accept(superVisitor.get());
                    }
                };
            }
        };
    }

    private static ConstantDynamic getMethodConstant(String desc, String owner, String descriptor) {
        var callerDesc = MethodTypeDesc.ofDescriptor(desc);
        var declaredMethodArgs = new ConstantDesc[callerDesc.parameterCount() + 2];
        declaredMethodArgs[0] = ClassDesc.of(owner);
        declaredMethodArgs[1] = descriptor;
        for (int j = 0; j < callerDesc.parameterCount(); j++) {
            declaredMethodArgs[j + 2] = callerDesc.parameterType(j);
        }
        return BackendASM.ConstantsASM.toAsm(Constants.invokeConstant(
            Constants.from(Method.class),
            MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.VIRTUAL,
                Constants.from(Class.class),
                "getDeclaredMethod",
                Constants.from(MethodType.methodType(Method.class, String.class, Class[].class))
            ),
            declaredMethodArgs
        ));
    }

    private static @Nullable ExecutableElement findMethodMatching(MethodInsnNode methodInsn, Context.BinaryBridge binaryBridge, DescriptorTypeVisitor descriptors) {
        return findMethodMatching(methodInsn.owner, methodInsn.name, methodInsn.desc::equals, binaryBridge, descriptors);
    }

    private static @Nullable ExecutableElement findMethodMatching(String owner, String name, Predicate<String> desc, Context.BinaryBridge binaryBridge, DescriptorTypeVisitor descriptors) {
        var element = binaryBridge.elementByInternalName(owner);
        if (element != null) {
            ExecutableElement methodElement = null;
            for (var el : element.getEnclosedElements()) {
                if (el instanceof ExecutableElement executableElement) {
                    if (executableElement.getSimpleName().contentEquals(name) && desc.test(descriptors.descriptor(executableElement.asType()))) {
                        methodElement = executableElement;
                    }
                }
            }
            return methodElement;
        }
        return null;
    }

    private final Map<String, Type> UNBOXING_TYPES = Map.of(
        Type.getType(Boolean.class).getInternalName(), Type.getType(boolean.class),
        Type.getType(Byte.class).getInternalName(), Type.getType(byte.class),
        Type.getType(Character.class).getInternalName(), Type.getType(char.class),
        Type.getType(Short.class).getInternalName(), Type.getType(short.class),
        Type.getType(Integer.class).getInternalName(), Type.getType(int.class),
        Type.getType(Long.class).getInternalName(), Type.getType(long.class),
        Type.getType(Float.class).getInternalName(), Type.getType(float.class),
        Type.getType(Double.class).getInternalName(), Type.getType(double.class)
    );

    private final Map<String, String> UNBOXING_NAMES = Map.of(
        Type.getType(Boolean.class).getInternalName(), "booleanValue",
        Type.getType(Byte.class).getInternalName(), "byteValue",
        Type.getType(Character.class).getInternalName(), "charValue",
        Type.getType(Short.class).getInternalName(), "shortValue",
        Type.getType(Integer.class).getInternalName(), "intValue",
        Type.getType(Long.class).getInternalName(), "longValue",
        Type.getType(Float.class).getInternalName(), "floatValue",
        Type.getType(Double.class).getInternalName(), "doubleValue"
    );

    private @Nullable MethodTypeDesc findActualDescriptor(String owner, MethodNode method, MethodInsnNode methodInsn, MethodTypeDesc originalDescriptor, boolean varargs) {
        var analyzer = new Analyzer<>(new PolymorphicInterpreter(Opcodes.ASM9, Objects.requireNonNull(commonSuperClassFinder)));
        var toRemove = new ArrayDeque<AbstractInsnNode>();
        var errored = false;
        try {
            var frames = analyzer.analyze(owner, method);
            var stackAtInsn = frames[method.instructions.indexOf(methodInsn)];
            var descriptor = originalDescriptor;
            for (int i = 0; i < descriptor.parameterCount(); i++) {
                var stackIndex = stackAtInsn.getStackSize() - descriptor.parameterCount() + i;
                var typeAtIndex = stackAtInsn.getStack(stackIndex);
                if (typeAtIndex.getType() != null) {
                    descriptor = descriptor.changeParameterType(i, ClassDesc.ofDescriptor(typeAtIndex.getType().getDescriptor()));
                    if (typeAtIndex.getBoxing() != null) {
                        toRemove.add(typeAtIndex.getBoxing());
                    }
                }
            }

            if (varargs) {
                // Before varargs call:
                // NEWARRAY / ANEWARRAY
                // [
                //   DUP
                //   ICONST/SIPUSH/BIPUSH/LDC
                //   <stuff>
                //   XASTORE
                // ] x N
                // Look at current stack, it's got just an array on the end
                // - Step backwards over XASTORE
                // - Record type
                // - Step backwards until index and dup and proper stack depth
                // - record index and dup instructions
                // - check for newarray
                var currentIndex = method.instructions.indexOf(methodInsn);
                var varargsTypes = new ArrayDeque<Type>();
                while (true) {
                    currentIndex -= 1;
                    if (currentIndex < 0) {
                        errored = true;
                        return null;
                        // TODO: log/warn?
                        // throw new IllegalStateException("Ran out stack trying to resolve varargs");
                    }
                    var instrBefore = method.instructions.get(currentIndex);
                    if (instrBefore.getOpcode() == Opcodes.NEWARRAY || instrBefore.getOpcode() == Opcodes.ANEWARRAY) {
                        toRemove.addFirst(instrBefore);
                        break;
                    }
                    if (instrBefore.getOpcode() >= Opcodes.IASTORE && instrBefore.getOpcode() <= Opcodes.SASTORE) {
                        toRemove.addFirst(instrBefore);
                        currentIndex -= 1;
                        if (currentIndex < 0) {
                            errored = true;
                            return null;
                            // TODO: log/warn?
                            // throw new IllegalStateException("Ran out stack trying to resolve varargs");
                        }
                        var currentStack = frames[currentIndex+1];
                        var topOfStack = currentStack.getStack(currentStack.getStackSize() - 1);
                        if (topOfStack.getType() != null) {
                            varargsTypes.addFirst(topOfStack.getType());
                            if (topOfStack.getBoxing() != null) {
                                toRemove.add(topOfStack.getBoxing());
                            }
                        } else {
                            errored = true;
                            return null;
                            // TODO: log/warn?
                            // throw new IllegalStateException("Could not determine type of varargs argument");
                        }
                        var targetStackSize = currentStack.getStackSize() - 1;
                        AbstractInsnNode currentInstr = null;
                        // ICONST_0 through ICONST_5, BIPUSH, SIPUSH, LDC
                        while (currentStack.getStackSize() > targetStackSize || isNonIntInstr(currentInstr)) {
                            currentIndex -= 1;
                            if (currentIndex < 0) {
                                errored = true;
                                return null;
                                // TODO: log/warn?
                                // throw new IllegalStateException("Ran out stack trying to resolve varargs");
                            }
                            currentInstr = method.instructions.get(currentIndex);
                            currentStack = frames[currentIndex+1];
                        }
                        toRemove.addFirst(currentInstr);
                        currentIndex -= 1;
                        if (currentIndex < 0) {
                            errored = true;
                            return null;
                            // TODO: log/warn?
                            // throw new IllegalStateException("Ran out stack trying to resolve varargs");
                        }
                        var hopefullyDup = method.instructions.get(currentIndex);
                        if (hopefullyDup.getOpcode() != Opcodes.DUP) {
                            errored = true;
                            return null;
                            // TODO: log/warn?
                            // throw new IllegalStateException("Expected DUP instruction in varargs");
                        }
                        toRemove.addFirst(hopefullyDup);
                    }
                }
                // And at this point there's an int instruction for the size, of some sort
                if (isNonIntInstr(method.instructions.get(currentIndex - 1))) {
                    errored = true;
                    return null;
                    // TODO: log/warn?
                    // throw new IllegalStateException("Expected array size instruction");
                }
                toRemove.addFirst(method.instructions.get(currentIndex - 1));
                descriptor = descriptor.dropParameterTypes(descriptor.parameterCount() - 1, descriptor.parameterCount());
                ClassDesc[] parameters = new ClassDesc[varargsTypes.size()];
                for (int i = 0; i < parameters.length; i++) {
                    parameters[i] = ClassDesc.ofDescriptor(Objects.requireNonNull(varargsTypes.pollFirst()).getDescriptor());
                }
                descriptor = descriptor.insertParameterTypes(descriptor.parameterCount(), parameters);
            }

            var idx = method.instructions.indexOf(methodInsn);
            AbstractInsnNode followingNode;
            while ((followingNode = method.instructions.get(idx + 1)) instanceof TypeInsnNode || followingNode instanceof MethodInsnNode) {
                if (followingNode instanceof TypeInsnNode typeInsnNode) {
                    if (typeInsnNode.getOpcode() == Opcodes.CHECKCAST) {
                        descriptor = descriptor.changeReturnType(typeInsnNode.desc.startsWith("[") ? ClassDesc.ofDescriptor(typeInsnNode.desc) : ClassDesc.of(typeInsnNode.desc.replace('/', '.')));
                        toRemove.add(typeInsnNode);
                        idx++;
                    } else {
                        break;
                    }
                } else {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) followingNode;
                    if (UNBOXING_NAMES.containsKey(methodInsnNode.owner) && methodInsnNode.name.equals(UNBOXING_NAMES.get(methodInsnNode.owner)) && methodInsnNode.desc.equals("()"+UNBOXING_TYPES.get(methodInsnNode.owner).getDescriptor())) {
                        descriptor = descriptor.changeReturnType(ClassDesc.ofDescriptor(
                            UNBOXING_TYPES.get(methodInsnNode.owner).getDescriptor()
                        ));
                        toRemove.add(methodInsnNode);
                        idx++;
                    } else {
                        break;
                    }
                }
            }
            return descriptor;
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        } finally {
            if (!errored) {
                for (var instr : toRemove) {
                    method.instructions.remove(instr);
                }
            }
        }
    }

    private static boolean isNonIntInstr(AbstractInsnNode currentInstr) {
        return (currentInstr.getOpcode() < Opcodes.ICONST_0 || currentInstr.getOpcode() > Opcodes.ICONST_5) && (currentInstr.getOpcode() < Opcodes.BIPUSH || currentInstr.getOpcode() > Opcodes.LDC);
    }
}
