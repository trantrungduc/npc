@echo off   
java -Dfile.encoding=UTF-8 -classpath "lib/*" -Dlog4j.configuration=file:conf/log4j.properties -Dlog4j.debug org.d.Mnp
pause