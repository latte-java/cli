/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader

/**
 * ASM ClassVisitor that builds a list of classes used within the Class being visited. This is essentially determining
 * the imports of the Class.
 *
 * @author Brian Pontarelli
 */
class ImportClassVisitor extends ClassVisitor {
  public Set<String> classes = new HashSet<>()

  ImportClassVisitor() {
    super(Opcodes.ASM9)
  }

  @Override
  void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
//    println "CV visit name=${name} signature=${signature} superName=${superName} interfaces=${interfaces}"
    if (superName) {
      classes.add(superName)
    }
    if (interfaces) {
      classes.addAll(interfaces)
    }
    if (signature) {
      new SignatureReader(signature).accept(new ImportSignatureVisitor(classes))
    }
  }

  @Override
  AnnotationVisitor visitAnnotation(String desc, boolean visible) {
//    println "CV visitAnnotation desc=${ASMTools.getClassName(desc)}"
    if (desc) {
      classes.add(ASMTools.getClassName(desc))
    }
    return new ImportAnnotationVisitor(classes)
  }

  @Override
  AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
//    println "CV visitTypeAnnotation desc=${ASMTools.getClassName(desc)}"
    if (desc) {
      classes.add(ASMTools.getClassName(desc))
    }
    return new ImportAnnotationVisitor(classes)
  }

  @Override
  FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
//    println "CV visitField name=${name} desc=${ASMTools.getClassName(desc)} signature=${signature}"
    if (desc) {
      String className = ASMTools.getClassName(desc)
      if (className) {
        classes.add(className)
      }
    }
    if (signature) {
      new SignatureReader(signature).accept(new ImportSignatureVisitor(classes))
    }
    return new ImportFieldVisitor(classes)
  }

  @Override
  MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
//    println "CV visitMethod name=${name} desc=${ASMTools.getClassName(desc)} signature=${signature} exceptions=${exceptions}"
    String returnClassName = ASMTools.getClassName(Type.getReturnType(desc))
    if (returnClassName) {
      classes.add(returnClassName)
    }

    Type[] types = Type.getArgumentTypes(desc);
    for (int i = 0; i < types.length; i++) {
      String argumentClassName = ASMTools.getClassName(types[i])
      if (argumentClassName) {
        classes.add(argumentClassName)
      }
    }

    if (exceptions) {
      classes.addAll(exceptions)
    }
    if (signature) {
      new SignatureReader(signature).accept(new ImportSignatureVisitor(classes))
    }

    return new ImportMethodVisitor(classes)
  }
}
