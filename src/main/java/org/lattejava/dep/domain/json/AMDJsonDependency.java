package org.lattejava.dep.domain.json;

import java.util.List;

public class AMDJsonDependency {
  public String id;

  public List<String> exclusions;

  public AMDJsonDependency() {
  }

  public AMDJsonDependency(String id, List<String> exclusions) {
    this.id = id;
    this.exclusions = exclusions;
  }
}
