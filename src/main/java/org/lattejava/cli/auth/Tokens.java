/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

/**
 * The OAuth tokens returned from a successful authorization-code exchange.
 *
 * @author Brian Pontarelli
 */
public record Tokens(String accessToken, String refreshToken) {
}
