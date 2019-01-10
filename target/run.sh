nohup /app/jdk1.8.0_121/bin/java -DAppNameNPC -Dfile.encoding=UTF-8 -Xmx2024m -classpath "lib/*" -Dlog4j.configuration=file:conf/log4j.properties org.d.Mnp > logs/stdout 2> logs/stderr < /dev/null &
PID=$!
echo $PID > logs/pid