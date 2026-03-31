package org.lattejava.dep.domain.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public class AMDJsonDependency {
  public String id;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<String> exclusions;

  public AMDJsonDependency() {
  }

  public AMDJsonDependency(String id, List<String> exclusions) {
    this.id = id;
    this.exclusions = exclusions;
  }
}
