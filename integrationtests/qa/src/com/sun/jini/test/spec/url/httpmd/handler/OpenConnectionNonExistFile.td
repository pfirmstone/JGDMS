testClass=OpenConnection
testCategories=url_spec,url

OpenConnection.Url=httpmd://localhost/nonexist;MD5=aaaaaaaa,comments
OpenConnection.ExpResult=java.io.IOException
testClassServer.dir=${java.io.tmpdir}
include0=../../url.properties
