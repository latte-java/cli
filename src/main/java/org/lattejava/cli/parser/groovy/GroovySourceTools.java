/*
 * Copyright (c) 2026, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.cli.parser.groovy;

import java.util.List;

import groovyjarjarantlr4.v4.runtime.CharStreams;
import groovyjarjarantlr4.v4.runtime.Token;
import org.apache.groovy.parser.antlr4.GroovyLexer;

/**
 * Utilities for locating blocks in Groovy source files using the Groovy ANTLR4 lexer. This provides accurate character
 * offsets that respect string literals, comments, and arbitrary whitespace.
 *
 * @author Brian Pontarelli
 */
public class GroovySourceTools {
  /**
   * A block found in Groovy source. The range {@code [start, end)} covers the entire construct from the identifier
   * through the closing brace. For example, for {@code dependencies {\n  ...\n}}, start is the index of 'd' and end
   * is the index after '}'.
   *
   * @param start The character index of the first token (the identifier).
   * @param end   The character index one past the closing brace.
   */
  public record Block(int start, int end) {}

  /**
   * Finds a top-level method-call block in the source, such as {@code project(...) \{} or {@code dependencies \{}. A
   * method-call block is an identifier followed by an optional parenthesized argument list and then a brace-delimited
   * body.
   * <p>
   * This method only matches at brace depth {@code nestingDepth}. For example, to find {@code dependencies} inside
   * {@code project(...) \{}, use {@code nestingDepth = 1}.
   *
   * @param source       The Groovy source text.
   * @param name         The identifier name to search for (e.g., "dependencies").
   * @param nestingDepth The brace nesting depth at which the identifier must appear. 0 = top-level, 1 = inside one
   *                     brace pair, etc.
   * @return The block, or null if not found.
   */
  public static Block findBlock(String source, String name, int nestingDepth) {
    List<? extends Token> tokens = tokenize(source);
    int depth = 0;

    for (int i = 0; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.getType() == GroovyLexer.LBRACE) {
        depth++;
      } else if (token.getType() == GroovyLexer.RBRACE) {
        depth--;
      } else if (depth == nestingDepth && isIdentifier(token, name)) {
        // Found the identifier at the right depth. Look ahead for optional (...) then {
        int j = skipWhitespace(tokens, i + 1);
        if (j >= tokens.size()) {
          continue;
        }

        // Skip optional parenthesized arguments
        if (tokens.get(j).getType() == GroovyLexer.LPAREN) {
          j = skipParens(tokens, j);
          if (j == -1) {
            continue;
          }
          j = skipWhitespace(tokens, j);
          if (j >= tokens.size()) {
            continue;
          }
        }

        // Must be followed by a brace
        if (tokens.get(j).getType() != GroovyLexer.LBRACE) {
          continue;
        }

        // Find the matching closing brace
        int closeIndex = findMatchingBrace(tokens, j);
        if (closeIndex == -1) {
          continue;
        }

        return new Block(token.getStartIndex(), tokens.get(closeIndex).getStopIndex() + 1);
      }
    }

    return null;
  }

  /**
   * Finds the closing brace that matches the opening brace at the given token index.
   *
   * @return The token index of the matching RBRACE, or -1 if not found.
   */
  private static int findMatchingBrace(List<? extends Token> tokens, int openIndex) {
    int depth = 0;
    for (int i = openIndex; i < tokens.size(); i++) {
      int type = tokens.get(i).getType();
      if (type == GroovyLexer.LBRACE) {
        depth++;
      } else if (type == GroovyLexer.RBRACE) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static boolean isIdentifier(Token token, String name) {
    return (token.getType() == GroovyLexer.Identifier || token.getType() == GroovyLexer.CapitalizedIdentifier)
        && token.getText().equals(name);
  }

  /**
   * Skips a balanced parenthesized group starting at the LPAREN at the given index.
   *
   * @return The token index after the matching RPAREN, or -1 if unbalanced.
   */
  private static int skipParens(List<? extends Token> tokens, int index) {
    int depth = 0;
    for (int i = index; i < tokens.size(); i++) {
      int type = tokens.get(i).getType();
      if (type == GroovyLexer.LPAREN) {
        depth++;
      } else if (type == GroovyLexer.RPAREN) {
        depth--;
        if (depth == 0) {
          return i + 1;
        }
      }
    }
    return -1;
  }

  /**
   * Skips NL and whitespace tokens starting at the given index.
   */
  private static int skipWhitespace(List<? extends Token> tokens, int index) {
    while (index < tokens.size()) {
      int type = tokens.get(index).getType();
      if (type != GroovyLexer.NL && type != GroovyLexer.WS && type != GroovyLexer.SEMI) {
        break;
      }
      index++;
    }
    return index;
  }

  private static List<? extends Token> tokenize(String source) {
    GroovyLexer lexer = new GroovyLexer(CharStreams.fromString(source));
    return lexer.getAllTokens();
  }
}
