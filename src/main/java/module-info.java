module dev.lukebemish.polymorphicsignatures {
    requires dev.lukebemish.javacpostprocessor;
    requires java.compiler;
    requires jdk.compiler;
    requires org.objectweb.asm;
    
    // Annotation-only deps
    requires static com.google.auto.service;
    requires static org.jspecify;
    requires static org.jetbrains.annotations;
    requires org.objectweb.asm.tree;
    requires org.objectweb.asm.tree.analysis;

    exports dev.lukebemish.polymorphicsignatures;
    
    provides dev.lukebemish.javacpostprocessor.PostProcessor with dev.lukebemish.polymorphicsignatures.impl.PolymorphicSignaturesPlugin;
}