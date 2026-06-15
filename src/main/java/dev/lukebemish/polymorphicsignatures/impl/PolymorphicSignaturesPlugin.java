package dev.lukebemish.polymorphicsignatures.impl;

import com.google.auto.service.AutoService;
import dev.lukebemish.javacpostprocessor.PostProcessor;
import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@AutoService(PostProcessor.class)
public final class PolymorphicSignaturesPlugin implements PostProcessor {
    private Elements elements;
    private Types types;
    private Context.CommonSuperClassFinder commonSuperClassFinder;
    
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
    }

    @Override
    public String name() {
        return "dev.lukebemish.polymorphic-signatures";
    }

    @Override
    public ClassVisitor visit(ClassVisitor next, String binaryName, JavaFileManager fileManager, JavaFileManager.Location location) {
        var elements = Objects.requireNonNull(this.elements);
        var types =  Objects.requireNonNull(this.types);
        var descriptors = new DescriptorTypeVisitor(elements, types);
        var annotationInfo = new AnnotationInfo(elements);
        return new ClassVisitor(Opcodes.ASM9, next) {
            @Override
            public MethodVisitor visitMethod(int access, String ownerName, String descriptor, String signature, String[] exceptions) {
                var node = new MethodNode(access, ownerName, descriptor, signature, exceptions);
                var superVisitor = super.visitMethod(access, ownerName, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, node) {
                    @Override
                    public void visitEnd() {
                        node.visitEnd();
                        if (node.visibleAnnotations != null) {
                            for (var annotation : node.visibleAnnotations) {
                                if (annotation.desc.equals(PolymorphicSignature.class.descriptorString())) {
                                    var receiverElement = findElement(binaryName, elements);
                                    if (receiverElement == null) {
                                        throw new IllegalArgumentException("Found @PolymorphicSignature method in local or anonymous class: " + binaryName);
                                    }
                                    // Replace method body with erroring one that reports lack of compilation with this post-processor
                                    var insnList = new InsnList();
                                    insnList.add(new TypeInsnNode(Opcodes.NEW, Type.getInternalName(AssertionError.class)));
                                    insnList.add(new InsnNode(Opcodes.DUP));
                                    insnList.add(new LdcInsnNode("This method should not be called directly; consuming code should be built with the polymorphic-signatures compiler plugin so that the relevant metafactory is used"));
                                    insnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, Type.getInternalName(AssertionError.class), "<init>", "(Ljava/lang/String;)V", false));
                                    insnList.add(new InsnNode(Opcodes.ATHROW));
                                    node.instructions = insnList;
                                }
                            }
                        }
                        // Do processing on node here
                        for (var insn : node.instructions) {
                            if (insn instanceof MethodInsnNode methodInsn) {
                                var ownerBinaryName = methodInsn.owner;
                                var element = findElement(ownerBinaryName.replace('/', '.'), elements);
                                if (element != null) {
                                    // Ignore cases of local/anonymous classes
                                    ExecutableElement methodElement = null;
                                    for (var el : element.getEnclosedElements()) {
                                        if (el instanceof ExecutableElement executableElement) {
                                            if (executableElement.getSimpleName().contentEquals(methodInsn.name) && descriptors.descriptor(executableElement.asType()).equals(methodInsn.desc)) {
                                                methodElement = executableElement;
                                            }
                                        }
                                    }
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

                                            descriptor = findActualDescriptor(ownerName, node, methodInsn, descriptor);
                                            
                                            var implName = (String) Objects.requireNonNull(mirror).getElementValues().get(annotationInfo.value).getValue();
                                            var implClazz = mirror.getElementValues().get(annotationInfo.clazz);
                                            var implClazzType = implClazz == null ? annotationInfo.self.asType() : (TypeMirror) implClazz.getValue();
                                            if (!(implClazzType instanceof DeclaredType declaredImplClazzType)) {
                                                throw new IllegalArgumentException(String.format("clazz of @PolymorphicSignature must be a declared type, not '%s'", implClazzType));
                                            }
                                            var implReceiver = types.isSameType(types.erasure(implClazzType), annotationInfo.self.asType()) ? methodInsn.owner : elements.getBinaryName((TypeElement) declaredImplClazzType.asElement()).toString().replace('.', '/');
                                            var isInterface = types.isSameType(types.erasure(implClazzType), annotationInfo.self.asType()) ? methodInsn.itf : declaredImplClazzType.asElement().getKind() == ElementKind.INTERFACE;
                                            node.instructions.set(insn, new InvokeDynamicInsnNode(
                                                    methodInsn.name,
                                                    descriptor.descriptorString(),
                                                    new Handle(
                                                            Opcodes.H_INVOKESTATIC,
                                                            implReceiver,
                                                            implName,
                                                            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class).descriptorString(),
                                                            isInterface
                                                    )
                                            ));
                                        }
                                    }
                                }
                            }
                        }
                        node.accept(superVisitor);
                    }
                };
            }
        };
    }

    private MethodTypeDesc findActualDescriptor(String owner, MethodNode method, MethodInsnNode methodInsn, MethodTypeDesc originalDescriptor) {
        var analyzer = new Analyzer<>(new PolymorphicInterpreter(Opcodes.ASM9, Objects.requireNonNull(commonSuperClassFinder)));
        try {
            var frames = analyzer.analyzeAndComputeMaxs(owner, method);
            var stackAtInsn = frames[method.instructions.indexOf(methodInsn)];
            var descriptor = originalDescriptor;
            for (int i = 0; i < descriptor.parameterCount(); i++) {
                var stackIndex = stackAtInsn.getStackSize() - descriptor.parameterCount() + i;
                var typeAtIndex = stackAtInsn.getStack(stackIndex);
                descriptor = descriptor.changeParameterType(i, ClassDesc.ofDescriptor(typeAtIndex.getType().getDescriptor()));
                if (typeAtIndex.getBoxing() != null) {
                    method.instructions.remove(typeAtIndex.getBoxing());
                }
            }
            return descriptor;
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
    }

    private static @Nullable TypeElement findElement(String ownerBinaryName, Elements elements) {
        var element = elements.getTypeElement(ownerBinaryName);
        if (element == null && ownerBinaryName.contains(".")) {
            var packageElement = elements.getPackageElement(
                    ownerBinaryName.substring(0, ownerBinaryName.lastIndexOf('.'))
            );
            var elementsFound = packageElement.getEnclosedElements();
            while (!elementsFound.isEmpty() && element == null) {
                for (var el : elementsFound) {
                    if (el instanceof TypeElement typeEl && elements.getBinaryName(typeEl).contentEquals(ownerBinaryName)) {
                        element = typeEl;
                    }
                }
                var newElementsFound = new ArrayList<Element>();
                for (var el : elementsFound) {
                    newElementsFound.addAll(el.getEnclosedElements());
                }
                elementsFound = newElementsFound;
            }
        }
        return element;
    }
}
