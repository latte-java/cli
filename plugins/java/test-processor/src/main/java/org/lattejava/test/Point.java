/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.test;

import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Fixture record annotated with the record-builder annotation processor. Compiling this with the
 * processor on {@code --processor-module-path} generates a {@code PointBuilder} class in this
 * package, which the test asserts exists.
 *
 * @author Brian Pontarelli
 */
@RecordBuilder
public record Point(int x, int y) {
}
