/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.TypePath

/**
 * ASM FieldVisitor that builds a list of classes used by the field being visited. This is essentially determining
 * the imports of a Class.
 *
 * @author Brian Pontarelli
 */
class ImportFieldVisitor extends FieldVisitor {
  private final Set<String> classes

  ImportFieldVisitor(Set<String> classes) {
    super(Opcodes.ASM9)
    this.classes = classes
  }

  @Override
  AnnotationVisitor visitAnnotation(String desc, boolean visible) {
//    println "FV visitAnnotation desc=${ASMTools.getClassName(desc)}"
    classes.add(ASMTools.getClassName(desc))
    return new ImportAnnotationVisitor(classes)
  }

  @Override
  AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
//    println "FV visitTypeAnnotation ${ASMTools.getClassName(desc)}"
    classes.add(ASMTools.getClassName(desc))
    return new ImportAnnotationVisitor(classes)
  }
}
