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
