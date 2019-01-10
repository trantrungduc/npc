package org.d;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteAddrValve;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.jasypt.util.text.BasicTextEncryptor;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

import com.google.common.collect.EvictingQueue;
import com.google.gson.Gson;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class Mnp {

	private Tomcat tomcat = null;
	
	public static final Logger logger = Logger.getLogger("filter");
	public static final Logger log = Logger.getLogger("process");
	public static EvictingQueue<String[]> requests = EvictingQueue.create(400000);
	public static Map<String,Object> global = new ConcurrentHashMap<String,Object>();
    public static Scheduler scheduler;
    public static final Gson gson = new Gson();
    public static Map<String,String> runningJob = new ConcurrentHashMap<String,String>();
    public static String path = "";
    
    public static PropertiesConfiguration props = null;
    public static PropertiesConfiguration i18n = null;
    public static SqlUtility utility = null;
    
    static BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
    
    public static String enc(String text){
		return (textEncryptor.encrypt(text));
	}
	public static String dec(String pass){
		return textEncryptor.decrypt(pass);
	}
	
	public static String eval(String input, Map<String,String> parameters){		
		VelocityContext vcontext = new VelocityContext(parameters);
		StringWriter sw = new StringWriter();
		Velocity.evaluate(vcontext, sw, "velocity",input);
		return sw.toString();
	}
	
	public static String fdate(String format){
    	DateFormat df = new SimpleDateFormat(format);
    	return df.format(new Date(System.currentTimeMillis()));
    }
	static Random random =new Random();
	public static String UUID(){
		return fdate("yyyyMMddHHmmss"+props.getString("ParticipantID.VNPT")+"SSSSSSS")+random.nextInt(10);
		
	}

	public void startTomcat() throws Exception {
		
		path = System.getProperty("user.dir");
		System.out.println("Load configuration api.properties!");
    	props = new PropertiesConfiguration();
    	props.setDelimiterParsingDisabled(true);
    	props.setEncoding("UTF-8");
    	props.load(path+"/conf/api.properties");
        props.setReloadingStrategy(new FileChangedReloadingStrategy());
        utility = new SqlUtility(props);
        textEncryptor.setPassword("vnptnet_modular_encrypt");
        
        i18n = new PropertiesConfiguration();
        i18n.setDelimiterParsingDisabled(true);
        i18n.setEncoding("UTF-8");
    	i18n.load(path+"/conf/vi_VN.properties");
        i18n.setReloadingStrategy(new FileChangedReloadingStrategy());
        
        Tomcat tomcat = new Tomcat();
		tomcat.setBaseDir(props.getString("module_temp_dir"));

        Connector connect = tomcat.getConnector();
        connect.setPort(props.getInt("module_port"));
        connect.setURIEncoding("UTF8");
        connect.setAttribute("maxThreads", props.getInt("module_max_connect"));
        connect.setAttribute("acceptCount", props.getInt("module_max_connect"));
        connect.setAttribute("connectionTimeout", props.getInt("module_connection_timeout"));
        connect.setProtocol("org.apache.coyote.http11.Http11NioProtocol");
        connect.setAttribute("keepAliveTimeout",props.getInt("module_connection_timeout"));
        connect.setAttribute("maxKeepAliveRequests",1);
        connect.setAttribute("minSpareThreads",props.getInt("module_init_connect"));
        connect.setAttribute("processorCache",props.getInt("module_max_connect"));
        
        List<Object> webapp = props.getList("module_context.name");
        List<Context> ctxs = new ArrayList<Context>();
        for (Object name:webapp){
        	Context ctx = tomcat.addWebapp(props.getString(name+".context_path"), path+props.getString(name+".location_path"));
        	ctxs.add(ctx);
        }
        
        if (props.containsKey("module_accept_ip")){
        	RemoteAddrValve valve = new RemoteAddrValve();
        	valve.setAllow(props.getString("module_accept_ip"));
        	for (Context ctx:ctxs){
        		ctx.getPipeline().addValve(valve);
            }
        }
        
        try{
			
	        System.out.println("Load jobs!");
			scheduler = new StdSchedulerFactory().getScheduler();
	    	scheduler.start();
	    	
	    	new Timer().schedule(new TimerTask() {
	
				@Override
				public void run() {
					String[] jobs = props.getStringArray("job.name");
					List<String> removes = new ArrayList<String>();
					for (String jobName: runningJob.keySet()){
						try {
							TriggerKey tk=TriggerKey.triggerKey(jobName,jobName);
							JobKey jk = JobKey.jobKey(jobName, jobName);
							String k = props.getString(jobName+".schedule")+"|"+props.getString(jobName+".script");
							
							if (!Arrays.asList(jobs).contains(jobName) || !k.equals(runningJob.get(jobName))){
								System.out.println("Stop job: "+jobName);
								scheduler.unscheduleJob(tk);
								scheduler.interrupt(jk);
								scheduler.deleteJob(jk);
								removes.add(jobName);
							}
						} catch (SchedulerException e) {
							System.out.println("Stop job: "+e.getMessage());
						}
					}
					for (String remove:removes){
						runningJob.remove(remove);
					}
					for (String jobName: jobs){
			    		if (!runningJob.containsKey(jobName)){
				        	System.out.println("Start job: "+jobName);
				        	JobDetail job = JobBuilder.newJob(GroovyJob.class).usingJobData("job", jobName).usingJobData("script", props.getString(jobName+".script")).usingJobData("scheduler", props.getString(jobName+".schedule")).withIdentity(jobName, jobName).build();
				        	Trigger trigger = TriggerBuilder.newTrigger()
				        			.withIdentity(jobName,jobName)
				        			.withSchedule(CronScheduleBuilder.cronSchedule(props.getString(jobName+".schedule")))
				        			.build();
				        	try {
								scheduler.scheduleJob(job, trigger);
								runningJob.put(jobName,props.getString(jobName+".schedule")+"|"+props.getString(jobName+".script"));
					        	
							} catch (SchedulerException e) {
								e.printStackTrace();
							}
			    		}
			        }
	    		}
			},0,10000);
		}catch(Exception e){
			e.printStackTrace();
		}
        
        tomcat.enableNaming();        
        tomcat.start();
        tomcat.getServer().await();
	}

	public void stopTomcat() throws Exception {
		tomcat.stop();
	}
	
	public void startPing(){
		
	}

	public static void main(String args[]) {
		try {
			Mnp tomcat = new Mnp();
			tomcat.startTomcat();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static boolean isJobRunning(JobExecutionContext ctx, String jobName){
        List<JobExecutionContext> currentJobs;
		try {
			currentJobs = ctx.getScheduler().getCurrentlyExecutingJobs();
			for (JobExecutionContext jobCtx : currentJobs) {
				String thisJobName = jobCtx.getJobDetail().getKey().getName();
	            String thisGroupName = jobCtx.getJobDetail().getKey().getGroup();
	            if (jobName.equalsIgnoreCase(thisJobName) && jobName.equalsIgnoreCase(thisGroupName)
	                    && !jobCtx.getFireTime().equals(ctx.getFireTime())) {
	                return true;
	            }
	        }
	        return false;
		} catch (SchedulerException e) {
			return false;
		}
    }
	
	public static Object shell(Binding binding, String script){
		GroovyShell shell = new GroovyShell(binding);
		try{
			File s = new File(path+"/conf/job/"+script);
			if (s.exists()){
				return gson.toJson(shell.evaluate(s));
			}else{
				return "Invalid groovy";
			}
		}catch(Exception e){
			return e.getMessage();
		}
	}
	
	public static String getXmlAttr(String xml, String path) throws DocumentException {
        SAXReader reader = new SAXReader();
        Document document = reader.read(new StringReader(xml));
        return ((Attribute)document.selectSingleNode(path)).getText();
    }
	@SuppressWarnings("unchecked")
	public static List<Node> getNodes(String xml,String path)throws DocumentException {
		SAXReader reader = new SAXReader();
        Document document = reader.read(new StringReader(xml));
        return document.selectNodes(path);
	}
	public static String getXmlText(String xml, String path) {
		try{
	        SAXReader reader = new SAXReader();
	        Document document = reader.read(new StringReader(xml));
	        return document.selectSingleNode(path).getText();
		}catch(Exception e){
			return "";
		}
	}
	public static boolean checkPrefix(String msisdn,String prefix){
		for (String s:prefix.split(",")){
			if (msisdn.startsWith(s)){
				return true;
			}
		}
		return false;
	}
	
	public static String post(Map<String,String> params) throws IOException {
		URL u = new URL(params.get("url"));
		
		HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty( "Content-Type", "text/xml; charset=utf-8" );
        if (params.containsKey("Content-Type")){
        	conn.setRequestProperty( "Content-Type", params.get("Content-Type") );
        }
        
        if (params.containsKey("SOAPAction")){
        	conn.setRequestProperty( "SOAPAction", params.get("SOAPAction") );
        }
        
        String data = FileUtils.readFileToString(new File(System.getProperty("user.dir")+"/conf/req/"+params.get("service")),"UTF-8");
        data = eval(data,params);
        
        conn.setRequestProperty( "Content-Length", String.valueOf(data.length()));
        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes("UTF-8"));
        os.flush();
        os.close();
        
        StringBuffer out = new StringBuffer();
        if (conn.getResponseCode()==200||conn.getResponseCode()==202){
	        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
	        String inputLine;
	        while ((inputLine = in.readLine()) != null) {
	        	out.append(inputLine+"\n");
	        }
	        in.close();
        }else{
        	BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream(),"UTF-8"));
	        String inputLine;
	        while ((inputLine = in.readLine()) != null) {
	        	out.append(inputLine+"\n");
	        }
	        in.close();
        }
        return out.toString();
	}
	public static String posts(Map<String,String> params) throws IOException, KeyManagementException, NoSuchAlgorithmException {
		long start=System.currentTimeMillis();
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier(){public boolean verify(String hostname, SSLSession session){return true;}});
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager()
        {
            public java.security.cert.X509Certificate[] getAcceptedIssuers()
            {
                return null;
            }

            public void checkClientTrusted(
                java.security.cert.X509Certificate[] certs, String authType)
            {
            }

            public void checkServerTrusted(
                java.security.cert.X509Certificate[] certs, String authType)
            {
            }
        } };
		SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        URL u = new URL(params.get("url"));
        HttpsURLConnection conn = (HttpsURLConnection) u.openConnection();
		conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty( "Content-Type", "text/xml; charset=utf-8" );
        if (params.containsKey("Content-Type")){
        	conn.setRequestProperty( "Content-Type", params.get("Content-Type") );
        }
        
        if (params.containsKey("SOAPAction")){
        	conn.setRequestProperty( "SOAPAction", params.get("SOAPAction") );
        }
        
        String data = FileUtils.readFileToString(new File(System.getProperty("user.dir")+"/conf/req/"+params.get("service")),"UTF-8");
        data = eval(data,params);
        Mnp.log.info("posts: "+params+"|"+data.replace("\n","").replace("\r",""));
        
        conn.setRequestProperty( "Content-Length", String.valueOf(data.length()));
        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes("UTF-8"));
        os.flush();
        os.close();
        
        StringBuffer out = new StringBuffer();
        if (conn.getResponseCode()==200){
	        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
	        String inputLine;
	        while ((inputLine = in.readLine()) != null) {
	        	out.append(inputLine+"\n");
	        }
	        in.close();
        }else{
        	BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream(),"UTF-8"));
	        String inputLine;
	        while ((inputLine = in.readLine()) != null) {
	        	out.append(inputLine+"\n");
	        }
	        in.close();
        }
        String s= out.toString();
        Mnp.requests.add(new String[]{params.get("trace_id"),params.get("tran_id"),params.get("service"),String.valueOf(System.currentTimeMillis()-start),"JOB",data,s});
        return s;
	}
	public static String get(String url){
		try {

		   HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		   con.setRequestMethod("GET");
		   con.setConnectTimeout(props.getInt("http_call_timeout"));
		   con.setReadTimeout(props.getInt("http_call_timeout"));
		   
		   BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		   String inputLine;
		   StringBuffer response = new StringBuffer();

		   while ((inputLine = in.readLine()) != null) {
			   response.append(inputLine);
		   }
		   in.close();
		   con.disconnect();
		   return response.toString();
		} catch (IOException e) {
		   return e.getMessage();
		}
	}

	public static String gets(String url) throws NoSuchAlgorithmException, KeyManagementException {
		try {
			HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			});
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
				}
			} };
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
			con.setRequestMethod("GET");
			/*con.setConnectTimeout(props.getInt("http_call_timeout"));
			con.setReadTimeout(props.getInt("http_call_timeout"));*/

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			con.disconnect();
			return response.toString();
		} catch (IOException e) {
			return e.getMessage();
		}
	}
	public static String gets(String url,String host, int port) throws NoSuchAlgorithmException, KeyManagementException {
		try {
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
			HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			});
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
				}
			} };
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection(proxy);
			con.setRequestMethod("GET");
			/*con.setConnectTimeout(props.getInt("http_call_timeout"));
			con.setReadTimeout(props.getInt("http_call_timeout"));*/

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			con.disconnect();
			return response.toString();
		} catch (IOException e) {
			return e.getMessage();
		}
	}
}