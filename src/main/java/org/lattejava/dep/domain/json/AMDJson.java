package org.lattejava.dep.domain.json;

import java.util.List;
import java.util.Map;

public class AMDJson {
  public List<AMDJsonLicense> licenses;

  public Map<String, List<AMDJsonDependency>> dependencyGroups;

  public AMDJson() {
  }

  public AMDJson(List<AMDJsonLicense> licenses, Map<String, List<AMDJsonDependency>> dependencyGroups) {
    this.licenses = licenses;
    this.dependencyGroups = dependencyGroups;
  }
}
