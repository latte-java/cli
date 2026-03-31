/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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
package org.lattejava.io;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * The information about a file that is collected from a FileSet. This includes the origin Path (which might be absolute
 * or relative to the directory of a FileSet) and a relative path (to the FileSet directory). This might also include
 * other file attributes that are part of the calculation including mode, ownership, etc.
 *
 * @author Brian Pontarelli
 */
public class FileInfo implements Comparable<FileInfo> {
  public FileTime creationTime;

  public String groupName;

  public FileTime lastAccessTime;

  public FileTime lastModifiedTime;

  public Path origin;

  public Set<PosixFilePermission> permissions;

  public Path relative;

  public Long size;

  public String userName;

  public FileInfo(Path origin, Path relative) {
    this.origin = origin;
    this.relative = relative;
  }

  @Override
  public int compareTo(FileInfo o) {
    return relative.compareTo(o.relative);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileInfo fileInfo = (FileInfo) o;

    if (creationTime != null ? !creationTime.equals(fileInfo.creationTime) : fileInfo.creationTime != null)
      return false;
    if (groupName != null ? !groupName.equals(fileInfo.groupName) : fileInfo.groupName != null) return false;
    if (lastAccessTime != null ? !lastAccessTime.equals(fileInfo.lastAccessTime) : fileInfo.lastAccessTime != null)
      return false;
    if (lastModifiedTime != null ? !lastModifiedTime.equals(fileInfo.lastModifiedTime) : fileInfo.lastModifiedTime != null)
      return false;
    if (origin != null ? !origin.equals(fileInfo.origin) : fileInfo.origin != null) return false;
    if (permissions != null ? !permissions.equals(fileInfo.permissions) : fileInfo.permissions != null) return false;
    if (relative != null ? !relative.equals(fileInfo.relative) : fileInfo.relative != null) return false;
    if (size != null ? !size.equals(fileInfo.size) : fileInfo.size != null) return false;
    if (userName != null ? !userName.equals(fileInfo.userName) : fileInfo.userName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = creationTime != null ? creationTime.hashCode() : 0;
    result = 31 * result + (groupName != null ? groupName.hashCode() : 0);
    result = 31 * result + (lastAccessTime != null ? lastAccessTime.hashCode() : 0);
    result = 31 * result + (lastModifiedTime != null ? lastModifiedTime.hashCode() : 0);
    result = 31 * result + (origin != null ? origin.hashCode() : 0);
    result = 31 * result + (permissions != null ? permissions.hashCode() : 0);
    result = 31 * result + (relative != null ? relative.hashCode() : 0);
    result = 31 * result + (size != null ? size.hashCode() : 0);
    result = 31 * result + (userName != null ? userName.hashCode() : 0);
    return result;
  }

  /**
   * <p>
   * Converts the file permissions of this FileInfo to a POSIX bit mapped mode. The bit map looks like this:
   * </p>
   * <pre>
   *   1_000_000_001_000_000
   * </pre>
   * <p>
   * The first bit is always set. The next three bits are the set UID bits, the next 3 bits are the set GID bits. The
   * next three bits are the owner permissions (read, write, execute), then the group permissions and finally the user
   * permissions.
   * </p>
   *
   * @return The POSIX mode bit map as an integer.
   */
  public int toMode() {
    return FileTools.toMode(permissions);
  }
}
