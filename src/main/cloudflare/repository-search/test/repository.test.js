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
