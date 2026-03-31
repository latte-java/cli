package org.lattejava.dep.domain.json;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public class AMDJson {
  public List<AMDJsonLicense> licenses;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Map<String, List<AMDJsonDependency>> dependencyGroups;

  public AMDJson() {
  }

  public AMDJson(List<AMDJsonLicense> licenses, Map<String, List<AMDJsonDependency>> dependencyGroups) {
    this.licenses = licenses;
    this.dependencyGroups = dependencyGroups;
  }
}
