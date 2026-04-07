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

import org.lattejava.cli.parser.groovy.GroovySourceTools.Block;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Tests GroovySourceTools.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class GroovySourceToolsTest {
  @Test
  public void findTopLevelBlock() {
    String source = """
        project(group: "org.example") {
          workflow { }
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "project", 0);
    assertNotNull(block);
    assertEquals(source.substring(block.start(), block.end()).trim(), """
        project(group: "org.example") {
          workflow { }
        }""".trim());
  }

  @Test
  public void findNestedBlock() {
    String source = """
        project(group: "org.example") {
          dependencies {
            group(name: "compile") {
              dependency(id: "org.foo:bar:1.0")
            }
          }
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "dependencies", 1);
    assertNotNull(block);
    String found = source.substring(block.start(), block.end());
    assert found.startsWith("dependencies");
    assert found.endsWith("}");
    assert found.contains("org.foo:bar:1.0");
  }

  @Test
  public void findBlockWithExtraWhitespace() {
    String source = """
        project(group: "org.example") {
          dependencies   {
            group(name: "compile") { }
          }
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "dependencies", 1);
    assertNotNull(block);
    assert source.substring(block.start(), block.end()).startsWith("dependencies");
  }

  @Test
  public void findBlockWithNewlineBeforeBrace() {
    String source = """
        project(group: "org.example")
        {
          dependencies
          {
            group(name: "compile") { }
          }
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "dependencies", 1);
    assertNotNull(block);
    assert source.substring(block.start(), block.end()).startsWith("dependencies");
  }

  @Test
  public void ignoresStringContents() {
    String source = """
        project(group: "org.example") {
          foo = "dependencies { something }"
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "dependencies", 1);
    assertNull(block, "Should not match 'dependencies' inside a string literal");
  }

  @Test
  public void ignoresComments() {
    String source = """
        project(group: "org.example") {
          // dependencies { something }
          /* dependencies { something } */
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "dependencies", 1);
    assertNull(block, "Should not match 'dependencies' inside comments");
  }

  @Test
  public void notFound() {
    String source = """
        project(group: "org.example") {
          workflow { }
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "dependencies", 1);
    assertNull(block);
  }

  @Test
  public void wrongDepth() {
    String source = """
        project(group: "org.example") {
          dependencies {
            group(name: "compile") { }
          }
        }
        """;
    // dependencies is at depth 1, not depth 0
    Block block = GroovySourceTools.findBlock(source, "dependencies", 0);
    assertNull(block);
  }

  @Test
  public void blockWithParensAndBraces() {
    String source = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }
        }
        """;
    Block block = GroovySourceTools.findBlock(source, "project", 0);
    assertNotNull(block);
    String found = source.substring(block.start(), block.end());
    assert found.startsWith("project(");
    assert found.contains("standard()");
    assert found.endsWith("}");
  }
}
