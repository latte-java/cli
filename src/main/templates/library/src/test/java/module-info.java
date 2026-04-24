module ${package}.tests {
  requires ${package};
  requires org.testng;
  opens ${package}.tests to org.testng;
}
