import module org.lattejava.web;

void main() {
  new Web()
      .get("/", (req, res) -> res.setBody("Hello, world!"))
      .start(8080);
}
