# Repository Search Cloudflare Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Cloudflare Worker that queries the Latte R2 repository to find artifact versions and returns JSON.

**Architecture:** A single Cloudflare Worker with an R2 bucket binding. It parses the artifact ID from query parameters, lists R2 objects under the corresponding prefix, extracts and sorts version strings, and returns JSON. Deployed to `api.lattejava.org`.

**Tech Stack:** Cloudflare Workers (JavaScript/ES modules), R2 bucket binding, Wrangler CLI for development and deployment.

---

## File Structure

- `src/main/cloudflare/repository-search/wrangler.toml` — Worker configuration with R2 binding
- `src/main/cloudflare/repository-search/src/index.js` — Worker entry point, request routing
- `src/main/cloudflare/repository-search/src/version.js` — Semantic version parsing and comparison
- `src/main/cloudflare/repository-search/src/repository.js` — R2 listing and version extraction logic
- `src/main/cloudflare/repository-search/test/version.test.js` — Tests for version comparison
- `src/main/cloudflare/repository-search/test/repository.test.js` — Tests for artifact ID parsing and version extraction
- `src/main/cloudflare/repository-search/package.json` — Dependencies (vitest for testing)

---

### Task 1: Scaffold the Worker project

**Files:**
- Create: `src/main/cloudflare/repository-search/package.json`
- Create: `src/main/cloudflare/repository-search/wrangler.toml`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "repository-search",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "wrangler dev",
    "deploy": "wrangler deploy",
    "test": "vitest run"
  },
  "devDependencies": {
    "vitest": "^3.1.1",
    "wrangler": "^4.14.0"
  }
}
```

- [ ] **Step 2: Create wrangler.toml**

```toml
name = "repository-search"
main = "src/index.js"
compatibility_date = "2025-04-01"

[[r2_buckets]]
binding = "REPOSITORY"
bucket_name = "latte-repository"
```

- [ ] **Step 3: Install dependencies**

Run: `cd src/main/cloudflare/repository-search && npm install`
Expected: `node_modules` created, lockfile written.

- [ ] **Step 4: Commit**

```bash
git add src/main/cloudflare/repository-search/package.json src/main/cloudflare/repository-search/wrangler.toml src/main/cloudflare/repository-search/package-lock.json
git commit -m "Scaffold repository-search Cloudflare Worker project"
```

---

### Task 2: Semantic version parsing and comparison

**Files:**
- Create: `src/main/cloudflare/repository-search/src/version.js`
- Create: `src/main/cloudflare/repository-search/test/version.test.js`

- [ ] **Step 1: Write the failing tests**

Create `test/version.test.js`:

```js
import { describe, it, expect } from 'vitest';
import { parseVersion, compareVersions, sortVersionsDescending } from '../src/version.js';

describe('parseVersion', () => {
  it('parses a simple version', () => {
    expect(parseVersion('1.2.3')).toEqual({ major: 1, minor: 2, patch: 3, preRelease: null });
  });

  it('parses a version with pre-release', () => {
    expect(parseVersion('1.0.0-beta')).toEqual({ major: 1, minor: 0, patch: 0, preRelease: 'beta' });
  });

  it('returns null for invalid version', () => {
    expect(parseVersion('notaversion')).toBeNull();
  });

  it('parses a two-segment version as major.minor.0', () => {
    expect(parseVersion('1.2')).toEqual({ major: 1, minor: 2, patch: 0, preRelease: null });
  });
});

describe('compareVersions', () => {
  it('sorts by major version', () => {
    expect(compareVersions('2.0.0', '1.0.0')).toBeGreaterThan(0);
  });

  it('sorts by minor version', () => {
    expect(compareVersions('1.2.0', '1.1.0')).toBeGreaterThan(0);
  });

  it('sorts by patch version', () => {
    expect(compareVersions('1.0.2', '1.0.1')).toBeGreaterThan(0);
  });

  it('equal versions return 0', () => {
    expect(compareVersions('1.0.0', '1.0.0')).toBe(0);
  });

  it('pre-release is lower than release', () => {
    expect(compareVersions('1.0.0-beta', '1.0.0')).toBeLessThan(0);
  });
});

describe('sortVersionsDescending', () => {
  it('sorts versions highest first', () => {
    expect(sortVersionsDescending(['0.1.0', '1.0.0', '0.2.0', '0.1.1']))
      .toEqual(['1.0.0', '0.2.0', '0.1.1', '0.1.0']);
  });

  it('filters out invalid versions', () => {
    expect(sortVersionsDescending(['1.0.0', 'garbage', '0.1.0']))
      .toEqual(['1.0.0', '0.1.0']);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd src/main/cloudflare/repository-search && npx vitest run`
Expected: FAIL — module `../src/version.js` not found.

- [ ] **Step 3: Write the implementation**

Create `src/version.js`:

```js
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd src/main/cloudflare/repository-search && npx vitest run`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/cloudflare/repository-search/src/version.js src/main/cloudflare/repository-search/test/version.test.js
git commit -m "Add semantic version parsing and comparison"
```

---

### Task 3: Repository listing and version extraction

**Files:**
- Create: `src/main/cloudflare/repository-search/src/repository.js`
- Create: `src/main/cloudflare/repository-search/test/repository.test.js`

- [ ] **Step 1: Write the failing tests**

Create `test/repository.test.js`:

```js
import { describe, it, expect } from 'vitest';
import { artifactIdToPrefix, extractVersions } from '../src/repository.js';

describe('artifactIdToPrefix', () => {
  it('converts a Latte artifact ID to an R2 prefix', () => {
    expect(artifactIdToPrefix('org.lattejava.plugin:dependency'))
      .toBe('org/lattejava/plugin/dependency/');
  });

  it('handles a simple group', () => {
    expect(artifactIdToPrefix('com.example:mylib'))
      .toBe('com/example/mylib/');
  });

  it('returns null for invalid ID (no colon)', () => {
    expect(artifactIdToPrefix('invalid')).toBeNull();
  });

  it('returns null for empty string', () => {
    expect(artifactIdToPrefix('')).toBeNull();
  });
});

describe('extractVersions', () => {
  it('extracts unique version strings from R2 object keys', () => {
    const prefix = 'org/lattejava/plugin/dependency/';
    const keys = [
      'org/lattejava/plugin/dependency/0.1.0/dependency-0.1.0.jar',
      'org/lattejava/plugin/dependency/0.1.0/dependency-0.1.0.jar.amd',
      'org/lattejava/plugin/dependency/0.1.1/dependency-0.1.1.jar',
      'org/lattejava/plugin/dependency/0.1.2/dependency-0.1.2.jar',
    ];
    expect(extractVersions(keys, prefix)).toEqual(['0.1.0', '0.1.1', '0.1.2']);
  });

  it('returns empty array when no keys match', () => {
    expect(extractVersions([], 'org/example/lib/')).toEqual([]);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd src/main/cloudflare/repository-search && npx vitest run`
Expected: FAIL — module `../src/repository.js` not found.

- [ ] **Step 3: Write the implementation**

Create `src/repository.js`:

```js
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd src/main/cloudflare/repository-search && npx vitest run`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/cloudflare/repository-search/src/repository.js src/main/cloudflare/repository-search/test/repository.test.js
git commit -m "Add R2 repository listing and version extraction"
```

---

### Task 4: Worker entry point and request handling

**Files:**
- Create: `src/main/cloudflare/repository-search/src/index.js`

- [ ] **Step 1: Write the Worker entry point**

Create `src/index.js`:

```js
import { artifactIdToPrefix, listVersions } from './repository.js';
import { sortVersionsDescending } from './version.js';

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname !== '/repository/search') {
      return new Response('Not Found', { status: 404 });
    }

    if (request.method !== 'GET') {
      return new Response('Method Not Allowed', { status: 405 });
    }

    const id = url.searchParams.get('id');
    if (!id) {
      return Response.json({
        fieldErrors: {
          id: [{ code: '[missing]id', message: 'The [id] parameter is required' }],
        },
      }, { status: 400 });
    }

    const prefix = artifactIdToPrefix(id);
    if (!prefix) {
      return Response.json({
        fieldErrors: {
          id: [{ code: '[invalid]id', message: 'The [id] parameter is not a valid artifact ID' }],
        },
      }, { status: 400 });
    }

    const latest = url.searchParams.get('latest');
    const versions = await listVersions(env.REPOSITORY, prefix);

    if (versions.length === 0) {
      return new Response(null, { status: 404 });
    }

    const sorted = sortVersionsDescending(versions);

    if (latest === 'true') {
      return Response.json({ id, versions: [sorted[0]] });
    }

    return Response.json({ id, versions: sorted });
  },
};
```

- [ ] **Step 2: Test locally with wrangler dev**

Run: `cd src/main/cloudflare/repository-search && npx wrangler dev`

Then in another terminal:
```bash
curl "http://localhost:8787/repository/search?id=org.lattejava.plugin:dependency&latest=true"
```

Expected: JSON response with the latest version, or 404 if the local R2 emulator has no data.

- [ ] **Step 3: Run all unit tests**

Run: `cd src/main/cloudflare/repository-search && npx vitest run`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/cloudflare/repository-search/src/index.js
git commit -m "Add Worker entry point with search endpoint"
```

---

### Task 5: Deploy and verify

- [ ] **Step 1: Deploy the Worker**

Run: `cd src/main/cloudflare/repository-search && npx wrangler deploy`
Expected: Worker deployed successfully. URL printed.

- [ ] **Step 2: Configure the custom domain**

Set up the `api.lattejava.org` route in Cloudflare dashboard to point to this Worker (or add to `wrangler.toml` if using routes).

- [ ] **Step 3: Verify with curl**

```bash
curl "https://api.lattejava.org/repository/search?id=org.lattejava.plugin:dependency&latest=true"
```

Expected:
```json
{"id":"org.lattejava.plugin:dependency","versions":["0.1.2"]}
```

```bash
curl "https://api.lattejava.org/repository/search?id=org.lattejava.plugin:dependency&latest=false"
```

Expected:
```json
{"id":"org.lattejava.plugin:dependency","versions":["0.1.2","0.1.1","0.1.0"]}
```

```bash
curl -s -o /dev/null -w "%{http_code}" "https://api.lattejava.org/repository/search?id=org.nonexistent:fake"
```

Expected: `404`

- [ ] **Step 4: Commit any final config changes**

```bash
git add src/main/cloudflare/repository-search/
git commit -m "Deploy repository search Worker to api.lattejava.org"
```
