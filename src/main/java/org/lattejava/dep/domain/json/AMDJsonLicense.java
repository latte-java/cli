package org.lattejava.dep.domain.json;

import com.fasterxml.jackson.annotation.JsonInclude;

public class AMDJsonLicense {
  public String type;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String text;

  public AMDJsonLicense() {
  }

  public AMDJsonLicense(String type, String text) {
    this.type = type;
    this.text = text;
  }
}
