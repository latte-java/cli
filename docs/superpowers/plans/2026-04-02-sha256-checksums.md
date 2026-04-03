# SHA256 Checksum Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace MD5-only checksum handling with algorithm-aware checksums: SHA256 for Latte repositories, SHA1/MD5 for Maven repositories.

**Architecture:** New `Algorithm` enum, `Checksum` class (replaces `MD5`), `ChecksumTools` (replaces `MD5Tools`), `ChecksumException` (replaces `MD5Exception`). `URLProcess` uses a template method `getChecksumAlgorithms()` overridden by `MavenProcess`. All consumers updated from MD5 types to Checksum types.

**Tech Stack:** Java 21, `java.security.MessageDigest`, TestNG

**Build/Test commands:** `JAVA_HOME=$(javaenv home) sb compile` and `JAVA_HOME=$(javaenv home) sb test`

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/org/lattejava/security/Algorithm.java` | Enum: SHA256, SHA1, MD5 with digest name, hex length, extension |
| Create | `src/main/java/org/lattejava/security/Checksum.java` | Replaces MD5: holds sum, bytes, algorithm. Factory + load + write methods |
| Create | `src/main/java/org/lattejava/security/ChecksumException.java` | Replaces MD5Exception |
| Create | `src/main/java/org/lattejava/security/ChecksumTools.java` | Replaces MD5Tools: streaming write+verify with configurable algorithm |
| Delete | `src/main/java/org/lattejava/security/MD5.java` | Replaced by Checksum |
| Delete | `src/main/java/org/lattejava/security/MD5Tools.java` | Replaced by ChecksumTools |
| Delete | `src/main/java/org/lattejava/security/MD5Exception.java` | Replaced by ChecksumException |
| Modify | `src/main/java/org/lattejava/net/NetTools.java` | Change MD5 params to Checksum |
| Modify | `src/main/java/org/lattejava/dep/workflow/process/URLProcess.java` | Add getChecksumAlgorithms(), update tryFetchCandidate |
| Modify | `src/main/java/org/lattejava/dep/workflow/process/MavenProcess.java` | Override getChecksumAlgorithms() for SHA1/MD5 |
| Modify | `src/main/java/org/lattejava/dep/workflow/FetchWorkflow.java` | Update throws clause |
| Modify | `src/main/java/org/lattejava/dep/workflow/Workflow.java` | Update MD5Exception to ChecksumException |
| Modify | `src/main/java/org/lattejava/dep/DefaultDependencyService.java` | Update MD5 references to Checksum |
| Modify | `src/main/java/org/lattejava/dep/DependencyService.java` | Update throws clauses |
| Modify | `src/main/java/org/lattejava/dep/workflow/process/Process.java` | Update throws clause if MD5Exception referenced |
| Modify | `src/main/java/org/lattejava/cli/runtime/Main.java` | Update MD5Exception catch |
| Modify | `src/main/java/org/lattejava/cli/runtime/DefaultBuildRunner.java` | Update MD5Exception catch |
| Modify | `src/main/java/org/lattejava/cli/runtime/BuildRunner.java` | Update throws clause |
| Modify | `src/main/java/org/lattejava/cli/runtime/DefaultProjectRunner.java` | Update throws clause |
| Modify | `src/main/java/org/lattejava/cli/runtime/ProjectRunner.java` | Update throws clause |
| Modify | `src/main/java/org/lattejava/cli/parser/BuildFileParser.java` | Update throws clause |
| Modify | `src/main/java/org/lattejava/cli/parser/groovy/GroovyBuildFileParser.java` | Update throws clause |
| Rename/Modify | `src/test/java/org/lattejava/security/MD5Test.java` → `ChecksumTest.java` | Test all algorithms |
| Modify | `src/test/java/org/lattejava/net/NetToolsTest.java` | Update MD5 to Checksum |
| Modify | `src/test/java/org/lattejava/dep/DefaultDependencyServiceTest.java` | Update MD5Exception to ChecksumException |
| Modify | `src/test/java/org/lattejava/dep/workflow/process/URLProcessTest.java` | Update if needed |
| Modify | `src/test/java/org/lattejava/dep/workflow/process/WorkflowTest.java` | Update if needed |
| Modify | `src/test/java/org/lattejava/cli/plugin/DefaultPluginLoaderTest.java` | Update MD5 to Checksum |
| Replace | `test-deps/latte/**/*.md5` → `.sha256` | SHA256 checksum files for Latte test data |
| Replace | `test-deps/integration/**/*.md5` → `.sha256` | SHA256 checksum files for integration test data |
| Replace | `src/test/plugin-repository/**/*.md5` → `.sha256` | SHA256 checksum files for plugin test data |
| Keep | `test-deps/maven/**/*.md5` | Maven test data stays MD5 |

---

### Task 1: Create Algorithm enum, ChecksumException, Checksum, and ChecksumTools

**Files:**
- Create: `src/main/java/org/lattejava/security/Algorithm.java`
- Create: `src/main/java/org/lattejava/security/ChecksumException.java`
- Create: `src/main/java/org/lattejava/security/Checksum.java`
- Create: `src/main/java/org/lattejava/security/ChecksumTools.java`

- [ ] **Step 1: Create Algorithm enum**

```java
package org.lattejava.security;

public enum Algorithm {
  SHA256("SHA-256", 64, ".sha256"),
  SHA1("SHA-1", 40, ".sha1"),
  MD5("MD5", 32, ".md5");

  public final String digestName;
  public final int hexLength;
  public final String extension;

  Algorithm(String digestName, int hexLength, String extension) {
    this.digestName = digestName;
    this.hexLength = hexLength;
    this.extension = extension;
  }
}
```

- [ ] **Step 2: Create ChecksumException**

```java
package org.lattejava.security;

public class ChecksumException extends RuntimeException {
  public ChecksumException(String message) {
    super(message);
  }
}
```

- [ ] **Step 3: Create Checksum**

```java
package org.lattejava.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.lattejava.lang.StringTools;

public final class Checksum {
  public final Algorithm algorithm;
  public final byte[] bytes;
  public final String sum;

  public Checksum(String sum, byte[] bytes, Algorithm algorithm) {
    this.sum = sum;
    this.bytes = bytes;
    this.algorithm = algorithm;
  }

  public static Checksum sha256(Path path) throws IOException {
    return forPath(path, Algorithm.SHA256);
  }

  public static Checksum sha1(Path path) throws IOException {
    return forPath(path, Algorithm.SHA1);
  }

  public static Checksum md5(Path path) throws IOException {
    return forPath(path, Algorithm.MD5);
  }

  public static Checksum forBytes(byte[] bytes, Algorithm algorithm) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm.digestName);
      digest.reset();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to locate " + algorithm.digestName + " algorithm");
    }

    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    DigestInputStream dis = new DigestInputStream(bais, digest);
    byte[] ba = new byte[1024];
    while (dis.read(ba, 0, 1024) != -1) {
    }
    dis.close();

    byte[] hash = digest.digest();
    return new Checksum(StringTools.toHex(hash), hash, algorithm);
  }

  public static Checksum forPath(Path path, Algorithm algorithm) throws IOException {
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("File to checksum doesn't exist [" + path.toAbsolutePath() + "]");
    }
    return forBytes(Files.readAllBytes(path), algorithm);
  }

  public static Checksum load(Path path, Algorithm algorithm) throws IOException {
    if (path == null || !Files.isRegularFile(path)) {
      return null;
    }

    String str = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
    if (str.length() < algorithm.hexLength) {
      throw new ChecksumException("Invalid " + algorithm.name() + " checksum [" + str + "] in file [" + path + "]. Expected at least " + algorithm.hexLength + " hex characters");
    }

    String sum = str.substring(0, algorithm.hexLength);
    return new Checksum(sum, StringTools.fromHex(sum), algorithm);
  }

  public static void write(Checksum checksum, Path path) throws IOException {
    String sum = checksum.sum;
    if (!sum.endsWith("\n")) {
      sum += "\n";
    }
    Files.write(path, sum.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Checksum checksum = (Checksum) o;
    return Arrays.equals(bytes, checksum.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
```

- [ ] **Step 4: Create ChecksumTools**

```java
package org.lattejava.security;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.lattejava.lang.StringTools;

public class ChecksumTools {
  public static Checksum write(InputStream is, OutputStream os, Checksum checksum) throws IOException {
    Algorithm algorithm = checksum != null ? checksum.algorithm : Algorithm.SHA256;
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm.digestName);
      digest.reset();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to locate " + algorithm.digestName + " algorithm");
    }

    DigestInputStream inputStream = new DigestInputStream(new BufferedInputStream(is), digest);
    inputStream.on(true);

    try (BufferedOutputStream bof = new BufferedOutputStream(os)) {
      byte[] b = new byte[8192];
      int len;
      while ((len = inputStream.read(b)) != -1) {
        bof.write(b, 0, len);
      }
    }

    if (checksum != null && checksum.bytes != null) {
      byte[] localHash = digest.digest();
      if (localHash != null && !Arrays.equals(localHash, checksum.bytes)) {
        throw new ChecksumException(algorithm.name() + " mismatch when writing from the InputStream to the OutputStream. Expected [" + StringTools.toHex(checksum.bytes) + "] but was [" + StringTools.toHex(localHash) + "]");
      }
    }

    byte[] hash = inputStream.getMessageDigest().digest();
    return new Checksum(StringTools.toHex(hash), hash, algorithm);
  }
}
```

- [ ] **Step 5: Compile**

Run: `JAVA_HOME=$(javaenv home) sb compile`
Expected: Compiles (old MD5 classes still exist, not yet deleted)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/security/Algorithm.java src/main/java/org/lattejava/security/Checksum.java src/main/java/org/lattejava/security/ChecksumException.java src/main/java/org/lattejava/security/ChecksumTools.java
git commit -m "Add Algorithm enum, Checksum, ChecksumTools, and ChecksumException"
```

---

### Task 2: Create Checksum tests

**Files:**
- Create: `src/test/java/org/lattejava/security/ChecksumTest.java`

- [ ] **Step 1: Write the test**

```java
package org.lattejava.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class ChecksumTest extends BaseUnitTest {
  @Test
  public void sha256() throws IOException {
    Path f = projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt");
    Checksum checksum = Checksum.sha256(f);
    assertNotNull(checksum);
    assertEquals(checksum.algorithm, Algorithm.SHA256);
    assertEquals(checksum.sum.length(), 64);
  }

  @Test
  public void sha1() throws IOException {
    Path f = projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt");
    Checksum checksum = Checksum.forPath(f, Algorithm.SHA1);
    assertNotNull(checksum);
    assertEquals(checksum.algorithm, Algorithm.SHA1);
    assertEquals(checksum.sum.length(), 40);
  }

  @Test
  public void md5() throws IOException {
    Path f = projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt");
    Checksum checksum = Checksum.md5(f);
    assertNotNull(checksum);
    assertEquals(checksum.algorithm, Algorithm.MD5);
    assertEquals(checksum.sum, "c0bfbec19e8e5578e458ce5bfee20751");
  }

  @Test
  public void loadBareHash() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();
    Checksum original = Checksum.sha256(projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt"));
    Files.writeString(tmp, original.sum);

    Checksum loaded = Checksum.load(tmp, Algorithm.SHA256);
    assertNotNull(loaded);
    assertEquals(loaded.sum, original.sum);
    assertEquals(loaded.algorithm, Algorithm.SHA256);
  }

  @Test
  public void loadHashWithFilename() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();
    Checksum original = Checksum.sha256(projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt"));
    Files.writeString(tmp, original.sum + "  some-file.txt\n");

    Checksum loaded = Checksum.load(tmp, Algorithm.SHA256);
    assertNotNull(loaded);
    assertEquals(loaded.sum, original.sum);
  }

  @Test
  public void loadInvalidHash() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();
    Files.writeString(tmp, "tooshort");

    try {
      Checksum.load(tmp, Algorithm.SHA256);
      fail("Should have thrown");
    } catch (ChecksumException e) {
      // Expected
    }
  }

  @Test
  public void loadNullPath() throws IOException {
    assertNull(Checksum.load(null, Algorithm.SHA256));
  }

  @Test
  public void writeAndLoad() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();

    Checksum original = Checksum.sha256(projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt"));
    Checksum.write(original, tmp);

    Checksum loaded = Checksum.load(tmp, Algorithm.SHA256);
    assertNotNull(loaded);
    assertEquals(loaded.sum, original.sum);
  }

  @Test
  public void streamingVerify() throws IOException {
    byte[] data = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    Checksum expected = Checksum.forBytes(data, Algorithm.SHA256);

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Checksum result = ChecksumTools.write(bais, baos, expected);

    assertNotNull(result);
    assertEquals(result.sum, expected.sum);
    assertEquals(baos.toByteArray(), data);
  }

  @Test
  public void streamingVerifyMismatch() {
    byte[] data = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    Checksum bad = new Checksum("0000000000000000000000000000000000000000000000000000000000000000", new byte[32], Algorithm.SHA256);

    try {
      ChecksumTools.write(new ByteArrayInputStream(data), new ByteArrayOutputStream(), bad);
      fail("Should have thrown");
    } catch (ChecksumException e) {
      // Expected
    }
  }
}
```

- [ ] **Step 2: Compile and run tests**

Run: `JAVA_HOME=$(javaenv home) sb test`
Expected: New ChecksumTest tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/lattejava/security/ChecksumTest.java
git commit -m "Add Checksum tests for all algorithms"
```

---

### Task 3: Update NetTools to use Checksum

**Files:**
- Modify: `src/main/java/org/lattejava/net/NetTools.java`

- [ ] **Step 1: Replace MD5 with Checksum**

In `NetTools.java`, replace all MD5 references:
- Import `Checksum` and `ChecksumTools` instead of `MD5`, `MD5Exception`, `MD5Tools`
- `downloadToPath(URI, String, String, MD5)` → `downloadToPath(URI, String, String, Checksum)`
- `fetchFile(URI, MD5)` → `fetchFile(URI, Checksum)`
- `fetchViaHttp(URI, String, String, MD5)` → `fetchViaHttp(URI, String, String, Checksum)`
- `writeToTempFile(InputStream, MD5)` → `writeToTempFile(InputStream, Checksum)`
- `MD5Tools.write(is, os, md5)` → `ChecksumTools.write(is, os, checksum)`

- [ ] **Step 2: Compile**

Run: `JAVA_HOME=$(javaenv home) sb compile`
Expected: Main compiles. Tests will fail (they still reference MD5).

- [ ] **Step 3: Update NetToolsTest**

In `NetToolsTest.java`:
- Replace `import org.lattejava.security.MD5` with `import org.lattejava.security.Checksum` and `import org.lattejava.security.Algorithm`
- Replace `import org.lattejava.security.MD5Exception` with `import org.lattejava.security.ChecksumException`
- `downloadToFileWithMD5` test: rename to `downloadToFileWithChecksum`, use `Checksum.forBytes(bytes, Algorithm.SHA256)` instead of `MD5.forBytes(bytes, "TestFile.txt")`
- `downloadToFileWithMD5Failure` test: rename to `downloadToFileWithChecksumFailure`, use `new Checksum("0".repeat(64), new byte[32], Algorithm.SHA256)` instead of `new MD5(...)`
- Replace `MD5Exception` catch with `ChecksumException`

- [ ] **Step 4: Compile and run tests**

Run: `JAVA_HOME=$(javaenv home) sb test`
Expected: NetToolsTest passes

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/net/NetTools.java src/test/java/org/lattejava/net/NetToolsTest.java
git commit -m "Update NetTools to use Checksum instead of MD5"
```

---

### Task 4: Update URLProcess and MavenProcess

**Files:**
- Modify: `src/main/java/org/lattejava/dep/workflow/process/URLProcess.java`
- Modify: `src/main/java/org/lattejava/dep/workflow/process/MavenProcess.java`

- [ ] **Step 1: Update URLProcess**

In `URLProcess.java`:
- Replace imports: `MD5` → `Checksum`, `Algorithm`; `MD5Exception` → `ChecksumException`
- Add protected method:

```java
protected List<Algorithm> getChecksumAlgorithms() {
  return List.of(Algorithm.SHA256);
}
```

- Rewrite `tryFetchCandidate` to loop over `getChecksumAlgorithms()`:

```java
private FetchResult tryFetchCandidate(ResolvableItem item, String candidateItem, PublishWorkflow publishWorkflow)
    throws ProcessFailureException {
  try {
    for (Algorithm algorithm : getChecksumAlgorithms()) {
      URI checksumURI = NetTools.build(url, item.group.replace('.', '/'), item.project, item.version, candidateItem + algorithm.extension);
      output.debugln("      - Download [" + checksumURI + "]");
      Path checksumFile = downloadToPath(checksumURI, username, password, null);
      if (checksumFile == null) {
        output.debugln("      - Not found");
        continue;
      }

      Checksum checksum;
      try {
        checksum = Checksum.load(checksumFile, algorithm);
      } catch (IOException e) {
        Files.delete(checksumFile);
        throw new ProcessFailureException(item, e);
      }

      URI itemURI = NetTools.build(url, item.group.replace('.', '/'), item.project, item.version, candidateItem);
      output.debugln("      - Download [" + itemURI + "]");
      Path itemFile;
      try {
        itemFile = downloadToPath(itemURI, username, password, checksum);
      } catch (ChecksumException e) {
        throw new ChecksumException(algorithm.name() + " mismatch when fetching item from [" + itemURI + "]");
      }

      if (itemFile != null) {
        output.infoln("Downloaded [%s]", itemURI);
        ResolvableItem matchedItem = candidateItem.equals(item.item) ? item : new ResolvableItem(item, candidateItem);
        ResolvableItem checksumItem = new ResolvableItem(item, candidateItem + algorithm.extension);
        publishWorkflow.publish(new FetchResult(checksumFile, itemSource, checksumItem));
        try {
          Path publishedFile = publishWorkflow.publish(new FetchResult(itemFile, itemSource, matchedItem));
          return new FetchResult(publishedFile != null ? publishedFile : itemFile, itemSource, matchedItem);
        } catch (ProcessFailureException e) {
          throw new ProcessFailureException(item, e);
        }
      } else {
        output.debugln("      - Not found");
      }

      return null;
    }

    return null;
  } catch (FileNotFoundException e) {
    return null;
  } catch (IOException e) {
    throw new ProcessFailureException(item, e);
  }
}
```

- Add import for `List`

- [ ] **Step 2: Update MavenProcess**

In `MavenProcess.java`, add the algorithm override:

```java
import java.util.List;
import org.lattejava.security.Algorithm;
```

```java
@Override
protected List<Algorithm> getChecksumAlgorithms() {
  return List.of(Algorithm.SHA1, Algorithm.MD5);
}
```

- [ ] **Step 3: Compile**

Run: `JAVA_HOME=$(javaenv home) sb compile`
Expected: Compiles (some tests may still reference old types)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/dep/workflow/process/URLProcess.java src/main/java/org/lattejava/dep/workflow/process/MavenProcess.java
git commit -m "Update URLProcess/MavenProcess with algorithm-aware checksum fetching"
```

---

### Task 5: Update all remaining MD5 references in production code

**Files:**
- Modify: `src/main/java/org/lattejava/dep/workflow/FetchWorkflow.java`
- Modify: `src/main/java/org/lattejava/dep/workflow/Workflow.java`
- Modify: `src/main/java/org/lattejava/dep/DefaultDependencyService.java`
- Modify: `src/main/java/org/lattejava/dep/DependencyService.java`
- Modify: `src/main/java/org/lattejava/dep/workflow/process/Process.java`
- Modify: `src/main/java/org/lattejava/cli/runtime/Main.java`
- Modify: `src/main/java/org/lattejava/cli/runtime/DefaultBuildRunner.java`
- Modify: `src/main/java/org/lattejava/cli/runtime/BuildRunner.java`
- Modify: `src/main/java/org/lattejava/cli/runtime/DefaultProjectRunner.java`
- Modify: `src/main/java/org/lattejava/cli/runtime/ProjectRunner.java`
- Modify: `src/main/java/org/lattejava/cli/parser/BuildFileParser.java`
- Modify: `src/main/java/org/lattejava/cli/parser/groovy/GroovyBuildFileParser.java`

- [ ] **Step 1: Replace all MD5Exception references**

In every file listed above:
- Replace `import org.lattejava.security.MD5Exception` with `import org.lattejava.security.ChecksumException`
- Replace `MD5Exception` with `ChecksumException` in throws clauses, catch blocks, and javadoc
- In `DefaultDependencyService.java`, also replace `import org.lattejava.security.MD5` with `import org.lattejava.security.Checksum` and `import org.lattejava.security.Algorithm`, and change `MD5.writeMD5(MD5.forPath(...), ...)` to `Checksum.write(Checksum.sha256(...), ...)`
- In `DefaultDependencyService.java`, rename references from `.md5` extension to `.sha256` in the publish method (the `ResolvableItem` for the checksum file)

Read each file first, find the MD5 references, and replace them.

- [ ] **Step 2: Compile**

Run: `JAVA_HOME=$(javaenv home) sb compile`
Expected: All main sources compile

- [ ] **Step 3: Commit**

```bash
git add src/main/java/
git commit -m "Replace all MD5Exception references with ChecksumException"
```

---

### Task 6: Delete old MD5 classes and update remaining test references

**Files:**
- Delete: `src/main/java/org/lattejava/security/MD5.java`
- Delete: `src/main/java/org/lattejava/security/MD5Tools.java`
- Delete: `src/main/java/org/lattejava/security/MD5Exception.java`
- Delete: `src/test/java/org/lattejava/security/MD5Test.java`
- Modify: `src/test/java/org/lattejava/dep/DefaultDependencyServiceTest.java`
- Modify: `src/test/java/org/lattejava/dep/workflow/process/URLProcessTest.java`
- Modify: `src/test/java/org/lattejava/dep/workflow/process/WorkflowTest.java`
- Modify: `src/test/java/org/lattejava/cli/plugin/DefaultPluginLoaderTest.java`

- [ ] **Step 1: Delete old MD5 classes**

```bash
rm src/main/java/org/lattejava/security/MD5.java
rm src/main/java/org/lattejava/security/MD5Tools.java
rm src/main/java/org/lattejava/security/MD5Exception.java
rm src/test/java/org/lattejava/security/MD5Test.java
```

- [ ] **Step 2: Update DefaultDependencyServiceTest**

Replace `import org.lattejava.security.MD5Exception` with `import org.lattejava.security.ChecksumException`. Replace all `MD5Exception` with `ChecksumException`. Update the error message assertions that reference "MD5 mismatch" to match the new format (e.g., `"SHA256 mismatch when fetching item from [...]"`).

- [ ] **Step 3: Update DefaultPluginLoaderTest**

Replace the `generateMD5Files` method:
- Rename to `generateChecksumFiles`
- Replace `MD5.writeMD5(MD5.forPath(...), ...)` with `Checksum.write(Checksum.sha256(...), ...)`
- Change all `.md5` file extensions to `.sha256` in the paths
- Update imports: `MD5` → `Checksum`

- [ ] **Step 4: Update URLProcessTest and WorkflowTest**

Replace any `MD5Exception` references with `ChecksumException`. Update imports.

- [ ] **Step 5: Compile and run tests**

Run: `JAVA_HOME=$(javaenv home) sb compile`
Expected: Compilation fails because test-deps still have `.md5` files instead of `.sha256`

- [ ] **Step 6: Commit test changes**

```bash
git add -A
git commit -m "Delete old MD5 classes, update all test references to Checksum"
```

---

### Task 7: Replace test data checksum files

**Files:**
- Replace: `test-deps/latte/**/*.md5` → `.sha256`
- Replace: `test-deps/integration/**/*.md5` → `.sha256`
- Replace: `src/test/plugin-repository/**/*.md5` → `.sha256`

- [ ] **Step 1: Generate .sha256 files for Latte test data and delete .md5 files**

```bash
# Generate .sha256 for all artifacts in test-deps/latte/
find test-deps/latte -name '*.md5' | while read md5file; do
  srcfile="${md5file%.md5}"
  sha256file="${srcfile}.sha256"
  if [ -f "$srcfile" ]; then
    shasum -a 256 "$srcfile" | awk '{print $1}' > "$sha256file"
  fi
  rm "$md5file"
done

# Same for integration
find test-deps/integration -name '*.md5' | while read md5file; do
  srcfile="${md5file%.md5}"
  sha256file="${srcfile}.sha256"
  if [ -f "$srcfile" ]; then
    shasum -a 256 "$srcfile" | awk '{print $1}' > "$sha256file"
  fi
  rm "$md5file"
done

# Same for plugin-repository
find src/test/plugin-repository -name '*.md5' | while read md5file; do
  srcfile="${md5file%.md5}"
  sha256file="${srcfile}.sha256"
  if [ -f "$srcfile" ]; then
    shasum -a 256 "$srcfile" | awk '{print $1}' > "$sha256file"
  fi
  rm "$md5file"
done
```

- [ ] **Step 2: Create intentionally-bad SHA256 files**

The `bad-amd-md5` and `bad-md5` test artifacts need corrupted checksum files:

```bash
echo "0000000000000000000000000000000000000000000000000000000000000bad" > test-deps/latte/org/lattejava/test/bad-amd-md5/1.0.0/bad-amd-md5-1.0.0.jar.amd.sha256
echo "0000000000000000000000000000000000000000000000000000000000000bad" > test-deps/latte/org/lattejava/test/bad-md5/1.0.0/bad-md5-1.0.0.jar.sha256
```

- [ ] **Step 3: Update error message assertions in DefaultDependencyServiceTest**

The tests `buildGraphFailureBadAMDMD5` and `buildGraphFailureBadMD5` assert specific error messages. Update them:
- `"MD5 mismatch when fetching item from [http://localhost:7042/test-deps/latte/org/lattejava/test/bad-amd-md5/1.0.0/bad-amd-md5-1.0.0.jar.amd]"` → `"SHA256 mismatch when fetching item from [http://localhost:7042/test-deps/latte/org/lattejava/test/bad-amd-md5/1.0.0/bad-amd-md5-1.0.0.jar.amd]"`
- `"MD5 mismatch when fetching item from [http://localhost:7042/test-deps/latte/org/lattejava/test/bad-md5/1.0.0/bad-md5-1.0.0.jar]"` → `"SHA256 mismatch when fetching item from [http://localhost:7042/test-deps/latte/org/lattejava/test/bad-md5/1.0.0/bad-md5-1.0.0.jar]"`

- [ ] **Step 4: Run full test suite**

Run: `JAVA_HOME=$(javaenv home) sb clean test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Replace .md5 with .sha256 for Latte/integration/plugin test data"
```
