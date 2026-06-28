package dev.lukebemish.polymorphicsignatures.utilities;

import dev.lukebemish.polymorphicsignatures.Bootstrap;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.runtime.SwitchBootstraps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Dynamic {
    public static CallSite dynamic(MethodHandles.Lookup lookup, String name, MethodType methodType, @Bootstrap.Receiver Method receiver, @Bootstrap.Receiver MethodHandles.Lookup receiverLookup) throws IllegalAccessException, NoSuchMethodException {
        if (!receiver.accessFlags().contains(AccessFlag.STATIC)) {
            return new ConstantCallSite(MethodHandles.dropArguments(
                MethodHandles.throwException(methodType.returnType(), IllegalArgumentException.class)
                    .bindTo(new IllegalArgumentException(String.format("Method '%s' is not static", receiver))),
                0, methodType.parameterArray()
            ));
        }
        var parameterCount = receiver.getParameterCount();
        var receiverName = receiver.getName();
        var candidates = Arrays.stream(receiver.getDeclaringClass().getDeclaredMethods())
            .filter(method -> {
                if (method.getName().equals(receiverName) &&
                    method.getParameterCount() == parameterCount &&
                    !method.equals(receiver)) {
                    for (int i = 0; i < parameterCount; i++) {
                        var paramType = method.getParameterTypes()[i];
                        var targetType = methodType.parameterType(i);
                        if (paramType.isPrimitive() || targetType.isPrimitive()) {
                            if (paramType.isPrimitive() && targetType.isPrimitive()) {
                                return paramType.equals(targetType);
                            }
                            if (paramType.isPrimitive()) {
                                paramType = BOX.get(paramType);
                            } else {
                                targetType = BOX.get(targetType);
                            }
                        }
                        // Comparison with only boxed types
                        if (!paramType.isAssignableFrom(targetType) && !targetType.isAssignableFrom(paramType)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }).toList();
        if (candidates.isEmpty()) {
            return new ConstantCallSite(unresolveable(methodType));
        }

        var hiddenImpl = generateSwitchImpl(lookup, receiverLookup, candidates, methodType);
        return new ConstantCallSite(hiddenImpl.findStatic(
            hiddenImpl.lookupClass(),
            "impl",
            methodType
        ));
    }

    private static SequencedMap<Class<?>, List<Method>> orderCandidates(List<Method> candidates, int parameter) {
        record Node(List<Node> in, Class<?> value) {}
        SequencedMap<Class<?>, Node> nodes = new LinkedHashMap<>();
        for (var candidate : candidates) {
            var clazz = candidate.getParameterTypes()[parameter];
            nodes.computeIfAbsent(clazz, k -> new Node(new ArrayList<>(), k));
        }
        for (var clazz : nodes.keySet()) {
            for (var node : nodes.values()) {
                if (node.value().isAssignableFrom(clazz)) {
                    node.in().add(nodes.get(clazz));
                }
            }
        }
        var nodesVals = new ArrayList<>(nodes.values());
        nodesVals.sort(Comparator.comparing(n -> n.in().size()));
        List<? extends Class<?>> classList = nodesVals.stream().map(Node::value).toList();
        Comparator<Class<?>> clazzSorter = (a, b) -> {
            if (a.isPrimitive() || b.isPrimitive()) {
                if (a.isPrimitive() && b.isPrimitive()) {
                    return 0; // equal or incompatible
                }
                if (a.isPrimitive()) {
                    a = BOX.get(a);
                }
                if (b.isPrimitive()) {
                    b = BOX.get(b);
                }
            }
            if (a.equals(b)) {
                return 0;
            } else if (a.isAssignableFrom(b)) {
                return 1;
            } else if (b.isAssignableFrom(a)) {
                return -1;
            }
            return 0;
        };
        Map<Class<?>, List<Method>> methodsByType = candidates.stream()
            .collect(Collectors.toMap(m -> m.getParameterTypes()[parameter], m -> {
                var list = new ArrayList<Method>();
                list.add(m);
                return list;
            }, (l1, l2) -> {
                l1.addAll(l2);
                return l1;
            }));
        List<SequencedSet<Class<?>>> orderedClassList = new ArrayList<>();
        outer: for (var c : classList) {
            if (orderedClassList.isEmpty()) {
                orderedClassList.add(new LinkedHashSet<>());
            }
            var last = orderedClassList.getLast();
            for (var o : last) {
                if (clazzSorter.compare(c, o) > 0) {
                    var newLast = new LinkedHashSet<Class<?>>();
                    newLast.add(c);
                    orderedClassList.add(newLast);
                    continue outer;
                }
                // The two have the same precedence
                // This means either:
                // - they're the same (we're all happy)
                // - they're two independent classes (no possible shared subtype exists, we're happy)
                // - one's an interface (uh oh...)
                // Dynamic#dynamic expects precedence to be resolvable from left to right
                // so every parameter type needs to be solvaable on its own this way.
                // An interface is fine, so long as no possible subtype of both types can exist.
                // So:
                // - compute sealed type trees
                // - look for mutual concrete types (failure)
                // - look for mutual open types (compatible classes, any interface)
                if (o.equals(c)) continue;
                if (o.isPrimitive() || c.isPrimitive()) continue;
                // TODO: arrays such
                if (!o.isInterface() && !c.isInterface()) continue;
                var oClasses = computeSealedHierarchy(o);
                var cClasses = computeSealedHierarchy(c);
                var concreteOClasses = oClasses.stream().filter(x -> x.accessFlags().contains(AccessFlag.ABSTRACT)).collect(Collectors.toSet());
                var concreteCClasses = cClasses.stream().filter(x -> x.accessFlags().contains(AccessFlag.ABSTRACT)).collect(Collectors.toSet());
                var openOClasses = oClasses.stream().filter(x -> !x.isSealed() && !x.accessFlags().contains(AccessFlag.FINAL)).collect(Collectors.toSet());
                var openCClasses = cClasses.stream().filter(x -> !x.isSealed() && !x.accessFlags().contains(AccessFlag.FINAL)).collect(Collectors.toSet());

                var sharedConcreteTypes = new LinkedHashSet<>(concreteCClasses);
                sharedConcreteTypes.retainAll(concreteOClasses);
                if (!sharedConcreteTypes.isEmpty()) {
                    throw new IllegalArgumentException("Ambiguity between methods "+sharedConcreteTypes.stream().flatMap(x -> methodsByType.get(x).stream()).toList()+"; cannot differentiate types "+sharedConcreteTypes.stream().toList());
                }

                if (!openOClasses.isEmpty() && !openCClasses.isEmpty()) {
                    for (var oX : openOClasses) {
                        if (oX.isInterface()) {
                            throw new IllegalArgumentException("Ambiguity between methods "+ Stream.concat(Stream.of(oX), openCClasses.stream()).flatMap(x -> methodsByType.get(x).stream()).toList()+"; cannot differentiate types "+Stream.concat(Stream.of(oX), openCClasses.stream()).toList());
                        }
                        for (var cX : openCClasses) {
                            if (cX.isInterface()) {
                                throw new IllegalArgumentException("Ambiguity between methods "+ Stream.concat(Stream.of(cX), openOClasses.stream()).flatMap(x -> methodsByType.get(x).stream()).toList()+"; cannot differentiate types "+Stream.concat(Stream.of(cX), openOClasses.stream()).toList());
                            }
                            if (cX.isAssignableFrom(oX) || oX.isAssignableFrom(cX)) {
                                throw new IllegalArgumentException("Ambiguity between methods "+ Stream.of(cX, oX).flatMap(x -> methodsByType.get(x).stream()).toList()+"; cannot differentiate types "+Stream.of(cX, oX).toList());
                            }
                        }
                    }
                }
            }
            last.add(c);
        }
        var map = new LinkedHashMap<Class<?>, List<Method>>();
        for (var clazz : classList) {
            map.put(clazz, methodsByType.get(clazz));
        }
        return map;
    }

    private static MethodHandles.Lookup generateSwitchImpl(MethodHandles.Lookup lookup, MethodHandles.Lookup receiverLookup, List<Method> candidates, MethodType methodType) {
        List<MethodHandle> handles = new ArrayList<>();
        var hiddenDesc = ClassDesc.of(lookup.lookupClass().getName()+"$Dynamic");
        var hiddenBytes = ClassFile.of().build(
            hiddenDesc,
            classBuilder -> {
                classBuilder.withMethod("impl", methodType.describeConstable().orElseThrow(), Modifier.PUBLIC | Modifier.STATIC, methodBuilder -> {
                    methodBuilder.withCode(code -> {
                        var finalLabel = code.newLabel();
                        switchTable(receiverLookup, code, candidates, 0, methodType, handles, finalLabel);
                        handles.add(unresolveable(methodType));
                        code.ldc(loadClassHandle(handles.size() - 1));
                        code.goto_(finalLabel);

                        code.labelBinding(finalLabel);
                        for (int i = 0; i < methodType.parameterCount(); i++) {
                            var type = methodType.parameterType(i);
                            code.loadLocal(TypeKind.from(type), i);
                        }
                        code.invokevirtual(
                            MethodHandle.class.describeConstable().orElseThrow(),
                            "invoke",
                            methodType.describeConstable().orElseThrow()
                        );
                        code.return_(TypeKind.from(methodType.returnType()));
                    });
                });
            }
        );
        return unchecked(() -> lookup.defineHiddenClassWithClassData(hiddenBytes, handles, false));
    }

    private static final Map<Class<?>, Class<?>> BOX = Map.of(
        byte.class, Byte.class,
        short.class, Short.class,
        int.class, Integer.class,
        long.class, Long.class,
        float.class, Float.class,
        double.class, Double.class,
        char.class, Character.class,
        boolean.class, Boolean.class
    );

    private static void switchTable(MethodHandles.Lookup lookup, CodeBuilder code, List<Method> candidates, int i, MethodType methodType, List<MethodHandle> handles, Label finalLabel) {
        if (i == methodType.parameterCount()) {
            MethodHandle handle;
            if (candidates.size() == 1) {
                handle = unchecked(() -> lookup.unreflect(candidates.getFirst()).asType(methodType));
            } else {
                handle = unresolveable(methodType);
            }
            handles.add(handle);
            code.ldc(loadClassHandle(handles.size() - 1));
            code.goto_(finalLabel);
            return;
        }

        var map = orderCandidates(candidates, i);
        var orderedValues = new ArrayList<>(map.sequencedValues());
        var orderedKeys = new ArrayList<>(map.sequencedKeySet());
        var defaultLabel = code.newLabel();
        var labels = IntStream.range(0, map.size()).mapToObj(_ -> code.newLabel()).toArray(Label[]::new);
        code.loadLocal(TypeKind.from(methodType.parameterType(i)), i);
        code.loadConstant(0);
        code.invokedynamic(DynamicCallSiteDesc.of(
            ConstantDescs.ofCallsiteBootstrap(
                SwitchBootstraps.class.describeConstable().orElseThrow(),
                "typeSwitch",
                CallSite.class.describeConstable().orElseThrow(),
                Object[].class.describeConstable().orElseThrow()
            ),
            "_",
            MethodTypeDesc.of(ClassDesc.ofDescriptor("I"), methodType.parameterType(i).describeConstable().orElseThrow(), ClassDesc.ofDescriptor("I")),
            map.sequencedKeySet().stream().map(c -> c.describeConstable().orElseThrow()).toArray(ConstantDesc[]::new)
        ));
        code.lookupswitch(defaultLabel, IntStream.range(0, map.size()).mapToObj(x -> SwitchCase.of(x, labels[x])).toList());
        for (int j = 0; j < labels.length; j++) {
            code.labelBinding(labels[j]);
            var methods = orderedValues.get(j);
            if (i+1 == methodType.parameterCount()) {
                MethodHandle handle;
                if (methods.size() == 1) {
                    handle = unchecked(() -> lookup.unreflect(methods.getFirst()).asType(methodType));
                } else {
                    handle = unresolveable(methodType);
                }
                handles.add(handle);
                code.ldc(loadClassHandle(handles.size() - 1));
                code.goto_(finalLabel);
            } else {
                switchTable(lookup, code, methods, i+1, methodType, handles, finalLabel);
                // Why do we duplicate this table here?
                // No good way to handle multiple-supertype-having types involving interfaces otherwise...
                List<Method> superclassCandidates = new ArrayList<>();
                for (var clazz : orderedKeys) {
                    if (!clazz.equals(orderedKeys.get(j)) && clazz.isAssignableFrom(orderedKeys.get(j))) {
                        superclassCandidates.addAll(map.get(clazz));
                    }
                }
                switchTable(lookup, code, superclassCandidates, i, methodType, handles, finalLabel);
                code.goto_(defaultLabel);
            }
        }
        code.labelBinding(defaultLabel);
    }

    private static DynamicConstantDesc<MethodHandle> loadClassHandle(int idx) {
        return DynamicConstantDesc.ofNamed(
            ConstantDescs.BSM_CLASS_DATA_AT,
            ConstantDescs.DEFAULT_NAME,
            MethodHandle.class.describeConstable().orElseThrow(),
            idx
        );
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void unchecked(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private static <T extends Throwable, R> R unchecked(CheckedSupplier<T, R> supplier) {
        return supplier.getUnchecked();
    }

    private interface CheckedSupplier<T extends Throwable, R> {
        R get() throws T;
        default R getUnchecked() {
            try {
                return get();
            } catch (Throwable e) {
                unchecked(e);
                // unreachable
                return null;
            }
        }
    }

    private static MethodHandle unresolveable(MethodType methodType) {
        return unchecked(() -> MethodHandles.lookup().findStatic(Dynamic.class, "cannotResolve", MethodType.methodType(Object.class, Object[].class)).withVarargs(true).asType(methodType));
    }

    private static <T> T cannotResolve(Object... args) throws NoSuchMethodException {
        throw new NoSuchMethodException(String.format("Cannot resolve method compatible with %s", Arrays.stream(args).map(Object::getClass).toList()));
    }

    private static SequencedSet<Class<?>> computeSealedHierarchy(Class<?> clazz) {
        SequencedSet<Class<?>> out = new LinkedHashSet<>();
        if (!clazz.accessFlags().contains(AccessFlag.ABSTRACT)) {
            out.add(clazz);
        }
        if (!clazz.isSealed() && !clazz.accessFlags().contains(AccessFlag.FINAL)) {
            out.add(clazz);
        }
        if (clazz.accessFlags().contains(AccessFlag.FINAL)) {
            return out;
        }
        var sealedSubclasses = clazz.getPermittedSubclasses();
        if (sealedSubclasses != null) {
            for (var subClazz : sealedSubclasses) {
                out.addAll(computeSealedHierarchy(subClazz));
            }
        }
        return out;
    }
}
