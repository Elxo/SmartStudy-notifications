[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
$env:JAVA_HOME            = "C:\Program Files\Java\jdk-26.0.1"
$env:PATH                 = "C:\tools\apache-maven-3.9.6\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS    = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
mvn javafx:run
$env:JAVA_TOOL_OPTIONS    = ""
