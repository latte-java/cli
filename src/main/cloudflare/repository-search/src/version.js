/**
 * Parses a version string into its components.
 * @param {string} version
 * @returns {{major: number, minor: number, patch: number, preRelease: string|null}|null}
 */
export function parseVersion(version) {
  const match = version.match(/^(\d+)\.(\d+)(?:\.(\d+))?(?:-(.+))?$/);
  if (!match) return null;
  return {
    major: parseInt(match[1], 10),
    minor: parseInt(match[2], 10),
    patch: match[3] != null ? parseInt(match[3], 10) : 0,
    preRelease: match[4] || null,
  };
}

/**
 * Compares two version strings. Returns positive if a > b, negative if a < b, 0 if equal.
 * Pre-release versions are lower than the same version without pre-release.
 * @param {string} a
 * @param {string} b
 * @returns {number}
 */
export function compareVersions(a, b) {
  const pa = parseVersion(a);
  const pb = parseVersion(b);
  if (!pa || !pb) return 0;

  if (pa.major !== pb.major) return pa.major - pb.major;
  if (pa.minor !== pb.minor) return pa.minor - pb.minor;
  if (pa.patch !== pb.patch) return pa.patch - pb.patch;

  // Both have pre-release: compare lexically
  if (pa.preRelease && pb.preRelease) return pa.preRelease.localeCompare(pb.preRelease);
  // Pre-release is lower than no pre-release
  if (pa.preRelease) return -1;
  if (pb.preRelease) return 1;

  return 0;
}

/**
 * Sorts version strings in descending order (highest first). Filters out unparseable versions.
 * @param {string[]} versions
 * @returns {string[]}
 */
export function sortVersionsDescending(versions) {
  return versions
    .filter(v => parseVersion(v) !== null)
    .sort((a, b) => compareVersions(b, a));
}
