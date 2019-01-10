import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import org.d.*;
import com.google.gson.reflect.TypeToken;

StringBuffer cdr = null;
while (Mnp.requests.size()>0){
	String[] request = Mnp.requests.poll();
	//Mnp.utility.updc("insert into requests values (?,?,?,SYSDATE,?,?,?,?,'IN')",[request[0],request[1],request[2],request[3],request[4],request[5],request[6]],"mnp",[6,7]);
	System.out.println(Arrays.asList(request));
}
return "OK";