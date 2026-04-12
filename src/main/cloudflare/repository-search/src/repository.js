/**
 * Converts a Latte artifact ID (e.g. "org.lattejava.plugin:dependency") to an R2 key prefix.
 * @param {string} id
 * @returns {string|null}
 */
export function artifactIdToPrefix(id) {
  if (!id || !id.includes(':')) return null;
  const [group, project] = id.split(':', 2);
  if (!group || !project) return null;
  return group.replace(/\./g, '/') + '/' + project + '/';
}

/**
 * Extracts unique version strings from R2 object keys under a given prefix.
 * @param {string[]} keys - R2 object keys
 * @param {string} prefix - The artifact prefix (e.g. "org/lattejava/plugin/dependency/")
 * @returns {string[]}
 */
export function extractVersions(keys, prefix) {
  const versions = new Set();
  for (const key of keys) {
    if (!key.startsWith(prefix)) continue;
    const rest = key.slice(prefix.length);
    const version = rest.split('/')[0];
    if (version) {
      versions.add(version);
    }
  }
  return [...versions].sort();
}

/**
 * Lists all versions of an artifact in the R2 bucket.
 * @param {R2Bucket} bucket - The R2 bucket binding
 * @param {string} prefix - The R2 key prefix
 * @returns {Promise<string[]>}
 */
export async function listVersions(bucket, prefix) {
  const keys = [];
  let cursor = undefined;
  let truncated = true;

  while (truncated) {
    const result = await bucket.list({ prefix, cursor });
    for (const obj of result.objects) {
      keys.push(obj.key);
    }
    truncated = result.truncated;
    cursor = result.cursor;
  }

  return extractVersions(keys, prefix);
}
