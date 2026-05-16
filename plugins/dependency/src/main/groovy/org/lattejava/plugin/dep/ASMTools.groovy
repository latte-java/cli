/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.objectweb.asm.Type

/**
 * @author Brian Pontarelli
 */
class ASMTools {
  public static String getClassName(String desc) {
    return getClassName(Type.getType(desc))
  }

  public static String getClassName(Type t) {
    switch (t.getSort()) {
      case Type.ARRAY:
        return getClassName(t.getElementType())
      case Type.OBJECT:
        return t.getClassName().replace('.', '/')
    }
    return null
  }
}
