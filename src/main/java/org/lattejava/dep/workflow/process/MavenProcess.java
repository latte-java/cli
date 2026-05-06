/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import java.util.List;

import org.lattejava.output.Output;
import org.lattejava.security.Algorithm;

/**
 * This class is a workflow process that attempts to download artifacts from a Maven repository via HTTP.
 * <p>
 * Maven's URL scheme is
 * <p>
 * <b>domain</b>/<b>group</b>/<b>project</b>/<b>version</b>/<b>name</b>-<b>version</b>.<b>type</b>
 *
 * @author Brian Pontarelli
 */
public class MavenProcess extends URLProcess {
  public MavenProcess(Output output, String url, String username, String password) {
    super(output, url, username, password, ItemSource.MAVEN);
  }

  @Override
  protected List<Algorithm> getChecksumAlgorithms() {
    return List.of(Algorithm.SHA1, Algorithm.MD5);
  }

  @Override
  public String toString() {
    return "Maven(" + url + ")";
  }
}
