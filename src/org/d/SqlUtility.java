package org.d;

import java.io.IOException;
import java.io.Writer;
import java.security.MessageDigest;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.jdbc.OracleTypes;
import oracle.jdbc.pool.OracleDataSource;
import oracle.jdbc.rowset.OracleCachedRowSet;
import oracle.sql.CLOB;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;

@SuppressWarnings("deprecation")
public class SqlUtility {
	PropertiesConfiguration props = null;
	Map<String,OracleDataSource> _ora = new HashMap<String,OracleDataSource>();	
	
	public SqlUtility(PropertiesConfiguration Props){
		props = Props;
	}
	
	public static void main(String[] args){
		System.out.println(getParamFromSql("begin ?:= crud.change_password('$!user_id','$!password1','$!password2'); end;"));
	}
	
	public static List<String> getParamFromSql(String SQL){
		List<String> allMatches = new ArrayList<String>();
		Matcher m = Pattern.compile("(\\$)(!*)([a-zA-Z0-9_]+)").matcher(SQL);
		while (m.find()) {
			allMatches.add(m.group(3));
		}
		return allMatches;
	}
	
	public Connection getConnection(String db, boolean reNew) throws SQLException{
		Connection conn = null;
		if (_ora.containsKey(db) && !reNew){
			conn = _ora.get(db).getConnection();
		}else{
			OracleDataSource _oracle = new OracleDataSource();
			java.util.Properties p = new java.util.Properties();
			p.setProperty("maxActive", props.getString(db+".maxActive"));
			p.setProperty("maxIdle", props.getString(db+".maxIdle"));
			p.setProperty("maxWait", props.getString(db+".maxWait"));
			p.setProperty("removeAbandoned", props.getString(db+".removeAbandoned"));
			p.setProperty("removeAbandonedTimeout", props.getString(db+".removeAbandonedTimeout"));
			_oracle.setUser(props.getString(db+".username"));
			_oracle.setPassword(Mnp.dec(props.getString(db+".password")));
			_oracle.setURL(props.getString(db+".url"));
			_ora.put(db,_oracle);
			conn = _oracle.getConnection();
		}
		conn.setAutoCommit(false);
		return conn;
	}
	
	public Connection getConnection(String db) throws SQLException{
		return getConnection(db,false);
	}
	
	public static String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());

            byte byteData[] = md.digest();

            //convert the byte to hex format method 1
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < byteData.length; i++) {
                sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).
                          substring(1));
            }

            //convert the byte to hex format method 2
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < byteData.length; i++) {
                String hex = Integer.toHexString(0xff & byteData[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String output = hexString.toString();
            return output;
        } catch (Exception ex) {
            return input;
        }
    }
	
	public String val(String Sql,List<String> InputParameters,String db) throws SQLException{  
		return value(Sql,InputParameters.toArray(),db);
	}
	
	public List<String> val(String Sql,List<String> InputParameters,String db,List<Integer> outs) throws SQLException{ 
		int[] is = new int[outs.size()];
		for (int i=0;i<is.length;i++){
			is[i] = outs.get(i);
		}
		return value(Sql,InputParameters.toArray(),db,is);
	}
	
	public List<Object> val(String Sql,List<String> InputParameters,String db,int outs) throws SQLException{ 
		return value(Sql,InputParameters.toArray(),db,outs);
	}
	
	public List<Map<String,String>> rf(String Sql,List<String> InputParameters,String db) throws SQLException{  
		return toList(ref(Sql,InputParameters.toArray(),db));
	}
	
	public List<Map<String,String>> rf(String Sql,List<String> InputParameters,String db, List<Integer> outs) throws SQLException{  
		int[] is = new int[outs.size()];
		for (int i=0;i<is.length;i++){
			is[i] = outs.get(i);
		}
		return toList(ref(Sql,InputParameters.toArray(),db,is));
	}
	
	public List<Map<String,String>> qry(String Sql,List<String> InputParameters,String db) throws SQLException{  
		return toList(query(Sql,InputParameters.toArray(),db));
	}
    
    public String value(String Sql,Object[] InputParameters,String db) throws SQLException{    	 
    	long t1 = System.currentTimeMillis();
    	Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
    	CallableStatement cstmt=conn.prepareCall(Sql);
    	if (InputParameters != null && InputParameters.length>0){
    		for (int i=0;i<InputParameters.length;i++){
    			cstmt.setObject(i+2,InputParameters[i]);
    		}
    	}
    	Logger.getLogger("sql").info(Sql+","+Arrays.toString(InputParameters));
    	cstmt.registerOutParameter(1,OracleTypes.VARCHAR);
		cstmt.execute();
		conn.commit();
		StringBuffer _stringResult=(new StringBuffer()).append(cstmt.getString(1));   
		cstmt.close();
		conn.close();
		long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|getValue: "+Sql);
	    return _stringResult.toString();
    }
    public String upd(String Sql,List<String> params,String db) throws SQLException{
    	return update(Sql,params.toArray(),db);
    }
    public String updc(String Sql,List<String> params,String db,List<Integer> clob) throws SQLException, IOException{
    	long t1 = System.currentTimeMillis();
    	Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
    	CallableStatement cstmt=conn.prepareCall(Sql);
    	if (params != null && params.size()>0){
    		for (int i=0;i<params.size();i++){
    			if (!clob.contains(i+1)){
    				cstmt.setObject(i+1,params.get(i));
    			}else{
    				try{
    				CLOB clob1 = CLOB.createTemporary(conn, true, CLOB.DURATION_SESSION);
    				Writer w = clob1.setCharacterStream( 1 );
                    w.write(params.get(i));
                    w.close();
                    cstmt.setClob(i+1,clob1);
    				}catch(Exception e){
    					e.printStackTrace();
    				}
    			}
    		}
    	}
    	int i = cstmt.executeUpdate();
		conn.commit();
		cstmt.close();
		conn.close();
		long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|update: "+Sql);
	    return String.valueOf(i);
    }
    public String update(String Sql,Object[] InputParameters,String db) throws SQLException{    	 
    	long t1 = System.currentTimeMillis();
    	Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
    	CallableStatement cstmt=conn.prepareCall(Sql);
    	if (InputParameters != null && InputParameters.length>0){
    		for (int i=0;i<InputParameters.length;i++){
    			cstmt.setObject(i+1,InputParameters[i]);
    		}
    	}
    	Logger.getLogger("sql").info(Sql+","+Arrays.toString(InputParameters));
    	int i = cstmt.executeUpdate();
		conn.commit();
		cstmt.close();
		conn.close();
		long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|update: "+Sql);
	    return String.valueOf(i);
    }
    
    public List<String> value(String Sql,Object[] InputParameters,String db,int[] outPosition) throws SQLException{    	 
    	long t1 = System.currentTimeMillis();
    	Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
    	CallableStatement cstmt=conn.prepareCall(Sql);
    	int prefix = 1;
    	for (int out:outPosition){
    		if (out==1){
    			prefix=2;
    		}
    	}
    	if (InputParameters != null && InputParameters.length>0){
    		for (int i=0;i<InputParameters.length;i++){
    			cstmt.setObject(i+prefix,InputParameters[i]);
    		}
    	}
    	Logger.getLogger("sql").info(Sql+","+Arrays.toString(InputParameters));
    	for (int out:outPosition){
    		cstmt.registerOutParameter(out,OracleTypes.VARCHAR);
    	}
		cstmt.execute();
		List<String> res = new ArrayList<String>();
    	for (int out:outPosition){
    		res.add(cstmt.getString(out));
    	}
	    conn.commit();
		cstmt.close();
		conn.close();
		long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|getValue: "+Sql);
    	return res;
    }
    
    public List<Object> value(String Sql,Object[] InputParameters,String db,int outPosition) throws SQLException{    	 
    	long t1 = System.currentTimeMillis();
    	Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
    	CallableStatement cstmt=conn.prepareCall(Sql);
    	cstmt.registerOutParameter(1,OracleTypes.VARCHAR);
    	if (InputParameters != null && InputParameters.length>0){
    		for (int i=0;i<InputParameters.length;i++){
    			cstmt.setObject(i+2,InputParameters[i]);
    		}
    	}
    	Logger.getLogger("sql").info(Sql+","+Arrays.toString(InputParameters));
    	cstmt.registerOutParameter(outPosition,OracleTypes.CURSOR);
    	cstmt.execute();
		List<Object> res = new ArrayList<Object>();
    	res.add( toList((ResultSet)cstmt.getObject(outPosition)) );
    	res.add(cstmt.getString(1));
    	conn.commit();
		cstmt.close();
		conn.close();
		long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|getValue: "+Sql);
    	return res;
    }
    
    public OracleCachedRowSet ref(String Sql,Object[] InputParameters,String db) throws SQLException{
		long t1 = System.currentTimeMillis();
		Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
		CallableStatement cstmt=conn.prepareCall(Sql);
		cstmt.registerOutParameter(1,OracleTypes.CURSOR);
    	if (InputParameters != null && InputParameters.length>0){
    		for (int i=0;i<InputParameters.length;i++){
    			cstmt.setObject(i+2,InputParameters[i]);
    		}
    	}
    	Logger.getLogger("sql").info("Call ref:"+Sql+","+Arrays.toString(InputParameters));
		cstmt.execute();
		conn.commit();
		ResultSet result = (ResultSet) cstmt.getObject(1);
		OracleCachedRowSet ocrset = new OracleCachedRowSet();
        ocrset.populate(result);result.close();      
        conn.close();
        long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|getRef: "+Sql);
        return ocrset;		
    }
    
    public OracleCachedRowSet ref(String Sql,Object[] InputParameters,String db, int[] outs) throws SQLException{
		long t1 = System.currentTimeMillis();
		Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
		CallableStatement cstmt=conn.prepareCall(Sql);
		cstmt.registerOutParameter(1,OracleTypes.CURSOR);
    	if (InputParameters != null && InputParameters.length>0){
    		for (int i=0;i<InputParameters.length;i++){
    			cstmt.setObject(i+2,InputParameters[i]);
    		}
    	}
    	for (int i:outs){
    		cstmt.registerOutParameter(i,OracleTypes.VARCHAR);
    	}
    	Logger.getLogger("sql").info("Call ref:"+Sql+","+Arrays.toString(InputParameters));
		cstmt.execute();
		conn.commit();
		ResultSet result = (ResultSet) cstmt.getObject(1);
		OracleCachedRowSet ocrset = new OracleCachedRowSet();
        ocrset.populate(result);result.close();      
        conn.close();
        long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|getRef: "+Sql);
        return ocrset;		
    }
    
    public OracleCachedRowSet query(String Sql,Object[] InputParameters,String db) throws SQLException{
		long t1 = System.currentTimeMillis();
		Connection conn = null;
    	try{
    		conn = getConnection(db);
    	}catch(SQLException ex){
    		Logger.getLogger("sql").info("sql|"+db+"|error: "+ex.getMessage()+", create new Data Source");
    		conn = getConnection(db,true);
    	}
		CallableStatement cstmt=conn.prepareCall(Sql);
    	if (InputParameters != null && InputParameters.length>0){
    		for (int i=0;i<InputParameters.length;i++){
    			cstmt.setObject(i+1,InputParameters[i]);
    		}
    	}	
		ResultSet result = cstmt.executeQuery();
		OracleCachedRowSet ocrset = new OracleCachedRowSet();
        ocrset.populate(result);result.close();
        conn.close();
        long t2 = System.currentTimeMillis();
    	Logger.getLogger("sql").info("sql|"+db+"|"+(t2-t1)+"|execQuery: "+Sql);
        return ocrset;
    }
    
    protected List<Map<String, String>> toList(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();

        int count = meta.getColumnCount();
        List<Map<String, String>> data = new ArrayList<Map<String, String>>();
        while (rs.next()) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            for (int i = 0; i < count; i++) {
                int columnNumber = i + 1;
                String columnName;
                try {
                    columnName = meta.getColumnLabel(columnNumber);
                } catch (SQLException e) {
                    columnName = meta.getColumnName(columnNumber);
                }
                row.put(columnName, rs.getString(columnNumber));
            }    
            data.add(row);
        }
        rs.close();
        return data;
    }
}