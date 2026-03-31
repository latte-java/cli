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
package org.lattejava.io.tar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.lattejava.io.FileTools;

/**
 * Tarball tools.
 *
 * @author Brian Pontarelli
 */
public class TarTools {
  /**
   * Untars a TAR file. This also handles tar.gz files by checking the file extension. If the file extension ends in .gz
   * it will read the tarball through a GZIPInputStream.
   *
   * @param file     The TAR file.
   * @param to       The directory to untar to.
   * @param useGroup Determines if the group name in the archive is used.
   * @param useOwner Determines if the owner name in the archive is used.
   * @throws IOException If the untar fails.
   */
  public static void untar(Path file, Path to, boolean useGroup, boolean useOwner) throws IOException {
    if (Files.notExists(to)) {
      Files.createDirectories(to);
    }

    InputStream is = Files.newInputStream(file);
    if (file.toString().endsWith(".gz")) {
      is = new GZIPInputStream(is);
    }

    try (TarArchiveInputStream tis = new TarArchiveInputStream(is)) {
      TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        Path entryPath = to.resolve(entry.getName());
        if (entry.isDirectory()) {
          // Skip directory entries that don't add any value
          if (entry.getMode() == 0 && entry.getGroupName() == null && entry.getUserName() == null) {
            continue;
          }

          if (Files.notExists(entryPath)) {
            Files.createDirectories(entryPath);
          }

          if (entry.getMode() != 0) {
            Set<PosixFilePermission> permissions = FileTools.toPosixPermissions(entry.getMode());
            Files.setPosixFilePermissions(entryPath, permissions);
          }

          if (useGroup && entry.getGroupName() != null && !entry.getGroupName().trim().isEmpty()) {
            GroupPrincipal group = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByGroupName(entry.getGroupName());
            Files.getFileAttributeView(entryPath, PosixFileAttributeView.class).setGroup(group);
          }

          if (useOwner && entry.getUserName() != null && !entry.getUserName().trim().isEmpty()) {
            UserPrincipal user = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(entry.getUserName());
            Files.getFileAttributeView(entryPath, PosixFileAttributeView.class).setOwner(user);
          }
        } else {
          if (Files.notExists(entryPath.getParent())) {
            Files.createDirectories(entryPath.getParent());
          }

          if (Files.isRegularFile(entryPath)) {
            if (Files.size(entryPath) == entry.getSize()) {
              continue;
            } else {
              Files.delete(entryPath);
            }
          }

          Files.createFile(entryPath);

          try (OutputStream os = Files.newOutputStream(entryPath)) {
            byte[] ba = new byte[1024];
            int read;
            while ((read = tis.read(ba)) != -1) {
              if (read > 0) {
                os.write(ba, 0, read);
              }
            }
          }

          if (entry.getMode() != 0) {
            Set<PosixFilePermission> permissions = FileTools.toPosixPermissions(entry.getMode());
            Files.setPosixFilePermissions(entryPath, permissions);
          }

          if (useGroup && entry.getGroupName() != null && !entry.getGroupName().trim().isEmpty()) {
            GroupPrincipal group = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByGroupName(entry.getGroupName());
            Files.getFileAttributeView(entryPath, PosixFileAttributeView.class).setGroup(group);
          }

          if (useOwner && entry.getUserName() != null && !entry.getUserName().trim().isEmpty()) {
            UserPrincipal user = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(entry.getUserName());
            Files.getFileAttributeView(entryPath, PosixFileAttributeView.class).setOwner(user);
          }
        }
      }
    }
  }
}
