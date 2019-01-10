package org.d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.codehaus.groovy.control.CompilationFailedException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import groovy.lang.Binding;

public class GroovyJob implements Job {
    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        String jobName = context.getJobDetail().getJobDataMap().getString("job");
        
        if (Mnp.isJobRunning(context, jobName)){
        	System.out.println("\nJob is running!");
        }else{
        	
        	Binding bind = new Binding();
			bind.setVariable("props", Mnp.props);
			bind.setVariable("utility",Mnp.utility);
			bind.setVariable("gson", Mnp.gson);
			bind.setVariable("global",Mnp.global);
			Iterator<?> k = Mnp.props.getKeys(jobName);
			while (k.hasNext()){
				String key = (String)k.next();
				bind.setVariable(key.replaceAll(jobName+".",""),Mnp.props.getString(key));
			}
			List<String> p = new ArrayList<String>();
			long start = System.currentTimeMillis();
			try {
				Object res_ = Mnp.shell(bind,Mnp.props.getString(jobName+".script"));
				long end = System.currentTimeMillis();
				System.out.println("\nJob "+jobName+" return: "+res_);
				p.add(jobName);p.add(String.valueOf(end-start));p.add(res_.toString());
			} catch (CompilationFailedException e) {
				p.add(jobName);p.add("0");p.add(e.getMessage());
				
				e.printStackTrace();
			}
        }
    }
    public static void main(String[] args){
    	System.out.println(Math.round(999999/10000));
    }
}
