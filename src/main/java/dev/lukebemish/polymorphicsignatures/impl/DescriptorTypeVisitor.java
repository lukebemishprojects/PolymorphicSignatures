package dev.lukebemish.polymorphicsignatures.impl;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

class DescriptorTypeVisitor {
    private final Elements elements;
    private final Types types;

    DescriptorTypeVisitor(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
    }
    
    public String descriptor(TypeMirror t) {
        var builder = new StringBuilder();
        visit(t, builder);
        return builder.toString();
    }

    void visit(TypeMirror t, StringBuilder builder) {
        var erasure = types.erasure(t);
        switch (erasure) {
            case DeclaredType declared when declared.asElement() instanceof TypeElement element ->
                    builder.append("L").append(elements.getBinaryName(element).toString().replace('.', '/')).append(';');
            case ArrayType array -> {
                builder.append('[');
                visit(array.getComponentType(), builder);
            }
            case ExecutableType executable -> {
                builder.append('(');
                for (var param : executable.getParameterTypes()) {
                    visit(param, builder);
                }
                builder.append(')');
                visit(executable.getReturnType(), builder);
            }
            default -> builder.append(switch (erasure.getKind()) {
                case INT -> "I";
                case BYTE -> "B";
                case BOOLEAN -> "Z";
                case VOID -> "V";
                case CHAR -> "C";
                case SHORT -> "S";
                case LONG -> "J";
                case FLOAT -> "F";
                case DOUBLE -> "D";
                default -> throw new IllegalArgumentException(String.format("Unable to form descriptor from type '%s'", erasure));
            });
        }
    }
}
