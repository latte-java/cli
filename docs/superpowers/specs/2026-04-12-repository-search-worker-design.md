# Repository Search Cloudflare Worker Design

## Overview

A Cloudflare Worker bound to the Latte R2 bucket that exposes a search API for finding the latest version of an artifact. This is a prerequisite for the `upgrade` CLI command.

## Endpoint

```
GET https://api.lattejava.org/repository/search?id=<id>&latest=true
```

- `id` (required): Artifact ID in Latte format, e.g. `org.lattejava.plugin:dependency`
- `latest` (required): When true, return only the latest version using semantic versioning. If false, return all versions.

## Artifact ID to R2 Prefix Mapping

The Latte artifact ID `org.lattejava.plugin:dependency` maps to the R2 key prefix `org/lattejava/plugin/dependency/`. The group portion (`org.lattejava.plugin`) has dots converted to slashes, then the project name is appended as another path segment.

## How It Finds the Latest Version

1. Parse the `id` parameter into group and project
2. Convert to R2 prefix: `{group-with-slashes}/{project}/`
3. List objects under that prefix in the R2 bucket
4. Extract unique version strings from the object keys (the path segment after the project name)
5. Sort versions semantically and return the highest

## Response

**Success (200):**
```json
{
  "id": "org.lattejava.plugin:dependency",
  "versions": ["0.1.2"]
}
```

**Not found (404):**
No response body.

**Bad request (400):**
```json
{
  "fieldErrors": {
    "id": [
      {"code": "[missing]id", "message": "The [id] parameter is required"}
    ]
  }
}
```

## Semantic Version Sorting

Versions are compared numerically by major, minor, patch. Pre-release versions (anything with a `-` suffix) are considered lower than the same version without a suffix, per semver rules.

## Infrastructure

- Cloudflare Worker with R2 bucket binding
- Deployed to `api.lattejava.org`
- The Worker only needs read access to the R2 bucket (`latte-repository`)

## Testing

Test manually with curl against known artifacts in the repository.
