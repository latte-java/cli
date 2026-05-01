module ${package}.tests {
  requires ${package};
  requires org.lattejava.http;
  requires org.lattejava.web;
  requires org.testng;

  opens ${package}.tests to org.testng;
}
