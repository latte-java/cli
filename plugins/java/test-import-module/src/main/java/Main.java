import module com.fasterxml.jackson.databind;
import module java.base;

void main() throws Exception {
  String markerPath = System.getProperty("latte.run.marker");
  if (markerPath == null) {
    throw new IllegalStateException("latte.run.marker system property is required");
  }

  Map<String, Object> data = new LinkedHashMap<>();
  data.put("greeting", "hello from import module");
  data.put("module", ObjectMapper.class.getModule().getName());

  String json = new ObjectMapper().writeValueAsString(data);
  Files.writeString(Path.of(markerPath), json);
}
