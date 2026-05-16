/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureVisitor

/**
 * ASM SignatureVisitor that builds a list of classes used within a generic signature of a method, field, or class.
 * This is essentially determining the imports of a Class.
 *
 * @author Brian Pontarelli
 */
class ImportSignatureVisitor extends SignatureVisitor {
  private final Set<String> classes

  ImportSignatureVisitor(Set<String> classes) {
    super(Opcodes.ASM9)
    this.classes = classes
  }

  @Override
  void visitClassType(String name) {
//    println "SV visitClassType name=${name}"
    classes.add(name)
  }

  @Override
  void visitInnerClassType(String name) {
//    println "SV visitInnerClassType name=${name}"
    classes.add(name)
  }

  @Override
  SignatureVisitor visitClassBound() {
    return this
  }

  @Override
  SignatureVisitor visitInterfaceBound() {
    return this
  }

  @Override
  SignatureVisitor visitSuperclass() {
    return this
  }

  @Override
  SignatureVisitor visitInterface() {
    return this
  }

  @Override
  SignatureVisitor visitParameterType() {
    return this
  }

  @Override
  SignatureVisitor visitReturnType() {
    return this
  }

  @Override
  SignatureVisitor visitExceptionType() {
    return this
  }

  @Override
  SignatureVisitor visitArrayType() {
    return this
  }

  @Override
  SignatureVisitor visitTypeArgument(char wildcard) {
    return this
  }
}
