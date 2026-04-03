# SHA256 Checksum Support Design

## Overview

Replace MD5-only checksum handling with algorithm-aware checksums. Latte repositories use SHA256 exclusively. Maven repositories use SHA1 with MD5 fallback.

## Checksum Abstraction

### `Algorithm` enum

```java
public enum Algorithm {
  SHA256("SHA-256", 64, ".sha256"),
  SHA1("SHA-1", 40, ".sha1"),
  MD5("MD5", 32, ".md5");

  public final String digestName;  // MessageDigest algorithm name
  public final int hexLength;      // expected hex string length
  public final String extension;   // file extension
}
```

### `Checksum` class (replaces `MD5`)

- Fields: `String sum`, `byte[] bytes`, `Algorithm algorithm`
- Factory methods: `Checksum.sha256(Path)`, `Checksum.sha1(Path)`, `Checksum.md5(Path)` — compute checksum from file
- `Checksum.forBytes(byte[], Algorithm)` — compute from byte array
- `Checksum.load(Path, Algorithm)` — parse a checksum file. Reads the first N characters (based on `algorithm.hexLength`), ignores the rest of the line. Validates hex length.
- `Checksum.write(Checksum, Path)` — write bare hex hash to file

### `ChecksumTools` class (replaces `MD5Tools`)

Same streaming `write(InputStream, OutputStream, Checksum)` method. Uses `algorithm.digestName` to create the `MessageDigest`. Throws `ChecksumException` on mismatch.

### `ChecksumException` class (replaces `MD5Exception`)

Same RuntimeException, algorithm-agnostic name.

## Fetch Flow Changes

### `URLProcess` (Latte repositories)

`tryFetchCandidate` calls a protected method `getChecksumAlgorithms()` which returns the ordered list of algorithms to try. `URLProcess` returns `[SHA256]`.

For each algorithm in the list:
1. Download `{item}{algorithm.extension}` (e.g., `foo.jar.sha256`)
2. If found, parse with `Checksum.load(path, algorithm)`
3. Download the artifact with checksum verification
4. Publish both checksum file and artifact
5. Return result

If no checksum file is found for any algorithm, return null (item not found).

### `MavenProcess` (Maven repositories)

Overrides `getChecksumAlgorithms()` to return `[SHA1, MD5]`. SHA1 preferred, MD5 fallback.

### `NetTools.downloadToPath()`

Takes `Checksum` instead of `MD5`. Same streaming verification through `ChecksumTools.write()`.

### `CacheProcess`

No changes to verification logic. Publishes whatever checksum file was fetched (carries the correct extension naturally through `ResolvableItem`).

## Publishing

`DefaultDependencyService.publish()` generates `.sha256` files for published Latte artifacts using `Checksum.sha256(path)`.

## Checksum File Format

All formats (`.sha256`, `.sha1`, `.md5`) use bare hex hash only when writing:
```
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

When reading, the parser reads the first N hex characters and ignores the rest of the line (handles Maven's format variations with filenames and dashes).

## Test Data

- `test-deps/latte/` — all `.md5` files replaced with `.sha256` files
- `test-deps/maven/` — `.md5` files stay as-is
- `test-deps/integration/` — `.md5` files replaced with `.sha256`
- `src/test/plugin-repository/` — `.md5` files replaced with `.sha256`
- Intentionally-corrupted checksum files (`bad-amd-md5`, `bad-md5`) get `.sha256` equivalents with bad hashes

## Testing

- **Checksum unit tests**: factory methods, file loading (bare hash, hash+filename formats), each algorithm
- **ChecksumTools unit tests**: streaming verify for each algorithm, mismatch throws `ChecksumException`
- **NetToolsTest**: updated to use `Checksum` instead of `MD5`
- **Integration tests**: `DefaultDependencyServiceTest` and `URLProcessTest` exercise full fetch with SHA256 for Latte, MD5 for Maven
- **DefaultPluginLoaderTest**: updated to use `Checksum.sha256()`
