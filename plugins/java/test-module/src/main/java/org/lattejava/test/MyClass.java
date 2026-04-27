package org.lattejava.test;

import module com.fasterxml.jackson.databind;
import module java.base;

public class MyClass {
  public String doSomething() {
    return "Hello World";
  }

  public static void main(String[] args) throws Exception {
    String markerPath = System.getProperty("latte.run.marker");
    if (markerPath == null) {
      throw new IllegalStateException("latte.run.marker system property is required");
    }
    String exitCodeStr = System.getProperty("latte.run.exitCode", "0");
    int exitCode = Integer.parseInt(exitCodeStr);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("pwd", new File("").getAbsolutePath());
    data.put("args", String.join(",", args));
    data.put("env.LATTE_RUN_TEST", String.valueOf(System.getenv("LATTE_RUN_TEST")));
    data.put("env.PATH.present", System.getenv("PATH") != null);

    String json = new ObjectMapper().writeValueAsString(data);
    Files.writeString(Path.of(markerPath), json);
    System.exit(exitCode);
  }
}
