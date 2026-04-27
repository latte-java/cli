import module org.lattejava.http;
import module org.lattejava.web;

void main() {
  new Web()
      .get("/", (req, res) -> res.getWriter().write("Hello, world!"))
      .start(8080);
}
