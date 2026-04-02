/*
 * Copyright (c) 2022, Inversoft Inc., All Rights Reserved
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
package org.lattejava.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.lattejava.domain.Version.PreRelease.PreReleasePart.NumberPreReleasePart;
import org.lattejava.domain.Version.PreRelease.PreReleasePart.StringPreReleasePart;

/**
 * This class models a simple three number version as well as any free form version String. It has two modes of
 * operation, strict and relaxed.
 * <p>
 * When this class is constructed in it tries everything in its power to figure out what the heck a version string is
 * and will only fail if the version string has two delimiters next to each other or begins/ends with a delimiter.
 * <p>
 * For the most part, this class correctly implements the Semantic Versioning system. This can be found here:
 * <p>
 * <a href="http://semver.org">http://semver.org</a>
 * <p>
 * The only notable except to this scheme is that Latte supports integration builds, which are not released versions,
 * but are local versions to a single developer or a team. Integration builds are always denoted by an additional
 * version specifier of <code>-{integration}</code>. For example, 1.0.0-{integration} denotes an integration build.
 *
 * @author Brian Pontarelli
 */
public class Version implements Comparable<Version> {
  public static final String INTEGRATION = "{integration}";

  public final int major;

  public final String metaData;

  public final int minor;

  public final int patch;

  public final PreRelease preRelease;

  // Constructor used for de-serialization
  public Version() {
    major = 0;
    metaData = null;
    minor = 0;
    patch = 0;
    preRelease = null;
  }

  /**
   * Constructs a version with the given major, minor and patch version numbers.
   *
   * @param major      The major version number.
   * @param minor      The minor version number.
   * @param patch      The patch version number.
   * @param preRelease The pre-release object.
   * @param metaData   The build meta data string.
   */
  public Version(int major, int minor, int patch, PreRelease preRelease, String metaData) {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new VersionException("Major, minor and patch must be positive integers");
    }

    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.preRelease = preRelease;
    this.metaData = metaData;
  }

  /**
   * Constructs a version by parsing the given String.
   *
   * @param version The version String to parse.
   * @throws VersionException If the string is incorrectly formatted and does not conform to the semantic versioning
   * scheme (starts with a delimiter (. or -), contains two delimiters in a row, doesn't have
   * proper pre-release or meta-data information).
   */
  public Version(String version) {
    char start = version.charAt(0);
    char end = version.charAt(version.length() - 1);
    if (start == '.' || start == '-' || start == '+' || end == '.' || end == '-' || end == '+') {
      throw new VersionException("Invalid Semantic Version string [" + version + "]. Version strings should not begin or end with . - or +");
    }

    StringBuilder num = new StringBuilder();
    Integer major = null;
    Integer minor = null;
    Integer patch = null;

    // Number loop
    int i = 0;
    for (; i < version.length(); i++) {
      char c = version.charAt(i);
      if (c == '.') {
        if (num.length() == 0) {
          throw new VersionException("Invalid Semantic Version string [" + version + "]. Two version delimiters should not be next to each other.");
        }

        if (major == null) {
          major = Integer.parseInt(num.toString());
        } else if (minor == null) {
          minor = Integer.parseInt(num.toString());
        } else if (patch == null) {
          patch = Integer.parseInt(num.toString());
        } else {
          throw new VersionException("Invalid Semantic Version string [" + version + "]. A version can only have at most 3 dotted parts <major>.<minor>.<patch>");
        }

        num.setLength(0);
      } else if (c == '-' || c == '+') {
        if (num.length() == 0) {
          throw new VersionException("Invalid Semantic Version string [" + version + "]. Two version delimiters should not be next to each other.");
        }

        break;
      } else if (Character.isDigit(c)) {
        num.append(c);
      } else {
        throw new VersionException("Invalid Semantic Version string [" + version + "]. Alphabetic characters are not allowed in the initial version string.");
      }
    }

    // Handle the final value
    if (num.length() > 0) {
      if (major == null) {
        major = Integer.parseInt(num.toString());
      } else if (minor == null) {
        minor = Integer.parseInt(num.toString());
      } else if (patch == null) {
        patch = Integer.parseInt(num.toString());
      } else {
        throw new VersionException("Invalid Semantic Version string [" + version + "]. A version can only have at most 3 dotted parts <major>.<minor>.<patch>");
      }
    }

    // Pre-release and meta
    int plus = version.indexOf('+', i);
    if (i < version.length() && version.charAt(i) == '-') {
      preRelease = new PreRelease((plus == -1) ? version.substring(i + 1) : version.substring(i + 1, plus));
    } else {
      preRelease = null;
    }

    if (plus != -1) {
      metaData = version.substring(plus + 1);
    } else {
      metaData = null;
    }

    this.major = major != null ? major : 0;
    this.minor = minor != null ? minor : 0;
    this.patch = patch != null ? patch : 0;
  }

  /**
   * Returns the value of the comparison between this Version and the given Object. This throws an exception if the
   * object given is not a Version.
   *
   * @param other The other Object to compare against.
   * @return A positive integer if this Version is larger than the given version. Zero if the given Version is the exact
   * same as this Version. A negative integer is this Version is smaller that the given Version.
   */
  public int compareTo(Version other) {
    int result = major - other.major;
    if (result == 0) {
      result = minor - other.minor;
    }

    if (result == 0) {
      result = patch - other.patch;
    }

    if (result == 0) {
      if (preRelease != null && other.preRelease != null) {
        result = preRelease.compareTo(other.preRelease);
      } else if (preRelease != null) {
        result = -1;
      } else if (other.preRelease != null) {
        result = 1;
      }
    }

    return result;
  }

  /**
   * Compares the given Object with this Version for equality.
   *
   * @param o The object to compare with this Version for equality.
   * @return True if they are both Versions and equal, false otherwise.
   */

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Version that = (Version) o;
    return major == that.major &&
        minor == that.minor &&
        patch == that.patch &&
        (preRelease != null ? preRelease.equals(that.preRelease) : that.preRelease == null);
  }

  public int getMajor() {
    return major;
  }

  public String getMetaData() {
    return metaData;
  }

  public int getMinor() {
    return minor;
  }

  public int getPatch() {
    return patch;
  }

  public PreRelease getPreRelease() {
    return preRelease;
  }

  /**
   * @return A valid hashcode for the Version.
   */

  public int hashCode() {
    int result;
    result = major;
    result = 31 * result + minor;
    result = 31 * result + patch;
    result = 31 * result + (preRelease != null ? preRelease.hashCode() : 0);
    return result;
  }

  /**
   * Performs semantic version compatibility checking. If this version is on the 0.x line, than the other version has to
   * be on the same minor line (e.g. 0.3 is not compatible with 0.4). Otherwise, the two versions have to be on the same
   * major line (e.g. 1.0 is not compatible with 2.0).
   *
   * @param other The other version to check against.
   * @return True if the versions are compatible, false if they aren't.
   */
  public boolean isCompatibleWith(Version other) {
    if (major == 0) {
      return minor == other.minor;
    }

    return major == other.major;
  }

  /**
   * @return True if this Version is an integration, false for all other types of versions.
   */
  public boolean isIntegration() {
    return preRelease != null && preRelease.isIntegration();
  }

  /**
   * @return True if this Version is a major, false for all other types of versions.
   */
  public boolean isMajor() {
    return minor == 0 && patch == 0 && preRelease == null;
  }

  /**
   * @return True if this Version is a minor, false for all other types of versions.
   */
  public boolean isMinor() {
    return minor > 0 && patch == 0 && preRelease == null;
  }

  /**
   * @return True if this Version is a patch, false for all other types of versions.
   */
  public boolean isPatch() {
    return patch > 0 && preRelease == null;
  }

  /**
   * @return True if this Version is a snapshot, false for all other types of versions.
   */
  public boolean isPreRelease() {
    return preRelease != null;
  }

  public Version toIntegrationVersion() {
    if (isIntegration()) {
      return this;
    }

    PreRelease integrationPreRelease = new PreRelease();
    if (preRelease != null) {
      integrationPreRelease.parts.addAll(preRelease.parts);
    }

    integrationPreRelease.parts.add(new StringPreReleasePart(INTEGRATION));

    return new Version(major, minor, patch, integrationPreRelease, metaData);
  }

  /**
   * Converts the version number to a string suitable for debugging.
   *
   * @return A String of the version number.
   */
  public String toString() {
    return "" + major + "." + minor + "." + patch + (preRelease != null ? "-" + preRelease : "") + (metaData != null ? "+" + metaData : "");
  }

  /**
   * Models the PreRelease portion of the Semantic Version String.
   *
   * @author Brian Pontarelli
   */
  public static class PreRelease implements Comparable<PreRelease> {
    public final List<PreReleasePart> parts = new ArrayList<>();

    public PreRelease(PreReleasePart... parts) {
      Collections.addAll(this.parts, parts);
    }

    public PreRelease(String spec) {
      char start = spec.charAt(0);
      char end = spec.charAt(spec.length() - 1);
      if (start == '.' || start == '-' || start == '+' || end == '.' || end == '-' || end == '+') {
        throw new VersionException("Invalid Semantic Version PreRelease string [" + spec + "]. PreRelease Version strings should not begin or end with . - or +");
      }

      StringBuilder part = new StringBuilder();
      int i = 0;
      for (; i < spec.length(); i++) {
        char c = spec.charAt(i);
        if (c == '.') {
          if (part.length() == 0) {
            throw new VersionException("Invalid Semantic Version PreRelease string [" + spec + "]. Two version separators (.) should not be next to each other.");
          }

          if (part.toString().equals(INTEGRATION)) {
            throw new VersionException("Invalid Semantic Version PreRelease string [" + spec + "]. The {integration} indicator must be the last PreRelease part.");
          }

          addPart(part);
        } else {
          part.append(c);
        }
      }

      addPart(part);
    }

    @Override
    public int compareTo(PreRelease o) {
      for (int i = 0; i < Integer.max(parts.size(), o.parts.size()); i++) {
        PreReleasePart mine = (i < parts.size()) ? parts.get(i) : null;
        PreReleasePart theirs = (i < o.parts.size()) ? o.parts.get(i) : null;
        if (mine == null && theirs == null) {
          return 0;
        }

        if (mine == null) {
          return -1;
        }

        if (theirs == null) {
          return 1;
        }

        int result = mine.compareTo(theirs);
        if (result != 0) {
          return result;
        }
      }

      return 0;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final PreRelease that = (PreRelease) o;
      return parts.equals(that.parts);
    }

    @Override
    public int hashCode() {
      return parts.hashCode();
    }

    /**
     * @return True if the PreRelease contains a part that is an integration indicator. This part must be the last part.
     */
    public boolean isIntegration() {
      return parts.size() > 0 && parts.get(parts.size() - 1).isIntegration();
    }

    @Override
    public String toString() {
      return String.join(".", parts.stream().map(Object::toString).collect(Collectors.toList()));
    }

    private void addPart(StringBuilder part) {
      if (part.length() == 0) {
        return;
      }

      String partStr = part.toString();
      try {
        int value = Integer.parseInt(partStr);
        parts.add(new NumberPreReleasePart(value));
      } catch (NumberFormatException e) {
        parts.add(new StringPreReleasePart(partStr));
      }

      part.setLength(0);
    }

    /**
     * Defines parts of a PreRelease version string.
     *
     * @author Brian Pontarelli
     */
    public interface PreReleasePart extends Comparable<PreReleasePart> {
      /**
       * @return True if this PreReleasePart is an integration build indicator.
       */
      boolean isIntegration();

      /**
       * @return True if this PreReleasePart is a numeric part.
       */
      boolean isNumber();

      /**
       * A number part of the PreRelease portion of the Semantic Version String.
       */
      class NumberPreReleasePart implements PreReleasePart {
        public final int value;

        public NumberPreReleasePart(int value) {
          this.value = value;
        }

        @Override
        public int compareTo(PreReleasePart o) {
          if (o instanceof StringPreReleasePart) {
            return -1;
          }

          return value - ((NumberPreReleasePart) o).value;
        }

        @Override
        public boolean equals(Object o) {
          if (this == o) {
            return true;
          }
          if (o == null || getClass() != o.getClass()) {
            return false;
          }

          final NumberPreReleasePart that = (NumberPreReleasePart) o;
          return value == that.value;
        }

        @Override
        public int hashCode() {
          return value;
        }

        @Override
        public boolean isIntegration() {
          return false;
        }

        @Override
        public boolean isNumber() {
          return true;
        }

        @Override
        public String toString() {
          return "" + value;
        }
      }

      /**
       * A String part of the PreRelease portion of the Semantic Version String.
       */
      class StringPreReleasePart implements PreReleasePart {
        public final String value;

        public StringPreReleasePart(String value) {
          this.value = value;
        }

        @Override
        public int compareTo(PreReleasePart o) {
          if (o instanceof NumberPreReleasePart) {
            return 1;
          }

          return value.compareTo(((StringPreReleasePart) o).value);
        }

        @Override
        public boolean equals(Object o) {
          if (this == o) {
            return true;
          }
          if (o == null || getClass() != o.getClass()) {
            return false;
          }

          final StringPreReleasePart that = (StringPreReleasePart) o;
          return value.equals(that.value);
        }

        @Override
        public int hashCode() {
          return value.hashCode();
        }

        @Override
        public boolean isIntegration() {
          return value.equals(INTEGRATION);
        }

        @Override
        public boolean isNumber() {
          return false;
        }

        @Override
        public String toString() {
          return value;
        }
      }
    }
  }
}
