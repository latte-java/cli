/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.objectweb.asm.*

/**
 * ASM AnnotationVisitor that builds a list of classes used within the annotation being visited. This is essentially
 * determining the imports of a Class.
 *
 * @author Brian Pontarelli
 */
class ImportAnnotationVisitor extends AnnotationVisitor {
  private final Set<String> classes

  ImportAnnotationVisitor(Set<String> classes) {
    super(Opcodes.ASM5)
    this.classes = classes
  }

  @Override
  void visit(String name, Object value) {
//    println "AV visit name=${name} value=${value}"
    if (value instanceof Type) {
//      println "Value is ${ASMTools.getClassName(value)}"
      classes.add(ASMTools.getClassName((Type) value))
    }
  }

  @Override
  void visitEnum(String name, String desc, String value) {
//    println "AV visitEnum name=${name} desc=${ASMTools.getClassName(desc)}"
    classes.add(ASMTools.getClassName(desc))
  }

  @Override
  AnnotationVisitor visitAnnotation(String name, String desc) {
//    println "AV visitAnnotation name=${name} desc=${ASMTools.getClassName(desc)}"
    classes.add(ASMTools.getClassName(desc))
    return this
  }

  @Override
  AnnotationVisitor visitArray(String name) {
//    println "AV visitArray name=${name}"
    return this
  }
}
