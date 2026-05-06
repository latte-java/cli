/*
 * Copyright (c) 2025-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import java.nio.file.Path;

import org.lattejava.dep.domain.ResolvableItem;

public record FetchResult(Path file, ItemSource source, ResolvableItem item) {
}
