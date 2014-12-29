package com.cn.dsa.programmatic;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import java.net.URLEncoder;

import java.text.SimpleDateFormat;
import java.text.ParseException;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.w3c.dom.*;
import org.xml.sax.SAXException;
import javax.xml.parsers.*;
import javax.xml.xpath.*;

import java.text.SimpleDateFormat;
import java.text.ParseException;

import org.json.XML;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.binary.Base64;

import com.cn.dsa.common.ServiceClient;

public class LiveRailClient {

    private static Logger log = Logger.getLogger(LiveRailClient.class.getName());
    //example: 2014-07-01 00:00:00
    private static String ISO_FORMAT = "yyyy-MM-dd HH:mm:ss";

    //report types
    public static String STAT_REPORT = "statistics";
    public static String PLAT_REPORT = "platform";
    public static String AGG_REPORT = "aggregated";
    public static String[] REPORT_TYPES = {STAT_REPORT};

    private static String ENDPOINT_KEY = "liverail.url";
    private static String ENTITY_ID_KEY = "entity.id";
    private static String USER_KEY = "liverail.user";
    private static String PWD_KEY = "liverail.pwd";
    
    private ServiceClient svcClient = new ServiceClient();
    private static Properties props = null;
    
    private String token = null;
    public void setToken(String token) {
        this.token = token;
    }
    public String getToken() {
        return token;
    }

    private String response = null;
    public void setResponse(String response) {
        this.response = response;
    }
    public String getResponse() {
        return response;
    }

    private Map<String,String> reportParams = new HashMap<String,String>();
    public void setReportParams(Map<String,String> reportParams) {
        this.reportParams = reportParams;
    }
    public Map<String,String> getReportParams() {
        return reportParams;
    }

    private Document xmlDoc = null;
    public void setXmlDoc(Document xmlDoc) {
        this.xmlDoc = xmlDoc;
    }
    public Document getXmlDoc() {
        return xmlDoc;
    }

    /**
     * Logs a user in to LiveRail API and returns status of login attempt.
     * @param String user
     * @param String pwd
     * @return boolean
     */
    public boolean login( String user, String pwd ) throws Exception {
        if(props == null) {
            loadProperties();
        }
        if(user == null) {
            log.info("LiveRail login: No user supplied");
            return false;
        }
        log.info("logging into LiveRail for "+user);


        //create login URL
        String endPoint = props.getProperty(ENDPOINT_KEY, "");
        String loginUrl = endPoint+"/login/";
        //String loginUrl = endPoint+"/say/hello/";
        
        //works with /login/
        String md5_pwd = DigestUtils.md5Hex(pwd);
        //log.info("MD5 hash of pwd "+pwd+" = "+md5_pwd);
        String body = "username="+URLEncoder.encode(user,"UTF-8")+"&password="+URLEncoder.encode(md5_pwd,"UTF-8"); 
        Map<String,String> headers = new HashMap<String,String>(); //empty

        //works with /say/hello/
        //String body = "username="+user+"&password="+pwd; 
        //Map<String,String> headers = new HashMap<String,String>();
        //headers.put("Authorization", createAuthString(user,pwd));

        log.info("POST body: "+body);
        String response = svcClient.doPostRequest(loginUrl, body, headers);

        log.info("Login response: "+response);

        /* load the XML */
        Document responseDoc = createDoc(response);
        if(responseDoc == null) {
            log.severe("No response from login API");
            return false;
        }   
        //check status
        if(successCheck(responseDoc)) {
            //if status is true, get the token
            XPath tokenPath = XPathFactory.newInstance().newXPath();
            XPathExpression tx = tokenPath.compile("/liverailapi/auth/token/text()");
            Object tokenObj = tx.evaluate(responseDoc, XPathConstants.STRING);
            if(tokenObj != null) {
                String token = (String)tokenObj;
                if(token == null || token.length() < 1) {
                    log.severe("no token found:\n"+response);
                    return false;
                }
                //log.info("login successful. Setting token to "+token);
                setToken(token);
                return true;
            }
        }

        log.severe("failed to login to LiveRail API:\n"+response);
        return false;

    } //end function login


    /**
     * statefully sets an entity.
     * @param String entity
     * @return boolean
     */
    public boolean setEntity( String entity ) throws Exception {
        if(props == null) {
            loadProperties();
        }

        //log.info("Attempting to set entity to: "+entity);
        String token = getToken();
        if( token == null ) {
            log.warning("setEntity: No token set");
            return false;
        }
        if(entity == null || entity.length() == 0) {
            log.warning("setEntity: invalid entity");
            return false;
        }

        String endPoint = props.getProperty(ENDPOINT_KEY, "");
        //create set entity URL
        String setEntityUrl = endPoint+"/set/entity/";

        Map<String,String> headers = new HashMap<String,String>();
        headers.put("LiveRailApiToken", Base64.encodeBase64String(token.getBytes("UTF-8")));
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        String entityMsg = "entity_id="+entity;
        log.info("POST body: "+entityMsg);
        String response = svcClient.doPostRequest(setEntityUrl, entityMsg, headers);

        log.info("Set Entity response: "+response);
        
        // load the XML
        Document responseDoc = createDoc(response);
        if(responseDoc == null) {
            log.severe("No response from set entity API");
            return false;
        }   
        //check status
        if(successCheck(responseDoc)) {
            //log.info("entity set successfully to: "+entity);
            return true;
        }
        
        log.severe("failed to set entity:\n"+response);
        return false;

    } //end function setEntity


    /**
     * make an api call
     * @param string path
     * @param Map params
     * @return String
     */
    public String callApi( String path, Map<String,String> params ) throws Exception {
        if(props == null) {
            loadProperties();
        }

        //log.info("Attempting to call API: "+path);
        String token = getToken();
        if( token == null ) {
            log.warning("callApi: No token set");
            return null;
        }
        if(params == null) {
            params = new LinkedHashMap<String,String>();
        }
        params.put("token", getToken());

        //create API URL
        String endPoint = props.getProperty(ENDPOINT_KEY, "");
        String apiUrl = endPoint+path;
        log.info("API URL: "+apiUrl);
        Map<String,String> headers = new HashMap<String,String>();
        headers.put("LiveRailApiToken", Base64.encodeBase64String(token.getBytes("UTF-8")));
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        String postBody = createPostBody(params);

        log.info("POST body: "+postBody);

        String response = svcClient.doPostRequest(apiUrl, postBody, headers);

        log.info("API response: "+response);

        return response;

    } //end function callApi



    public boolean logout() {
        boolean success = false;
        try {
            String logoutResponse = callApi("/logout", null);
            setToken(null);
        }
        catch(Exception lEx) {
            log.warning("could not logout: "+lEx.getMessage());
        }
        
        return true;
    }


    /**
     * Generates String for the HTTP Authorization Header
     * Creates MD5 hash of pwd, then concatenates that to user string with a colon 
     * then does Base64 encoding of whole string, then prepends Basic to that.
     * @param Document response
     * @return boolean success
     */
    private String createAuthString(String user, String pwd) throws Exception {
        //create MD5 Hash of password
        String md5_pwd = DigestUtils.md5Hex(pwd);
        //log.info("MD5 hash of pwd "+pwd+" = "+md5_pwd);
        //create Base64 encoding of user:md5_pwd
        String userPwd = user+":"+md5_pwd;
        String base64Auth = Base64.encodeBase64String(userPwd.getBytes("UTF-8"));
        //log.info("Base64 auth: "+base64Auth);
        return "Basic "+base64Auth;
    }

    /**
     * Checks the status element of the XML Document and returns boolean based on status
     * @param Document response
     * @return boolean success
     */
    private boolean successCheck(Document response) throws Exception {
        XPath statusPath = XPathFactory.newInstance().newXPath();
        XPathExpression xs = statusPath.compile("/liverailapi/status/text()");
        Object statusObj = xs.evaluate(response, XPathConstants.STRING);
        if(statusObj != null) {
            String statusVal = (String)statusObj;
            if(statusVal.equalsIgnoreCase("success")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Stores the response string, then creates Document from XML and store Document
     * @param String response
     * @return Document doc
     */
    private Document createDoc(String response) throws Exception {
        if(response == null) {
            return null;
        }
        InputStream stream = new ByteArrayInputStream(response.getBytes("UTF-8"));
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true); 
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(stream);
        return doc;  
    }


    private String createPostBody(Map<String,String> params) throws Exception {
        StringBuilder body = new StringBuilder();
        Iterator it = params.entrySet().iterator();
        //log.info(params.size()+" params to add");
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            String key = (String)pairs.getKey();
            String val = (String)pairs.getValue();
            String url_val = val;
            log.info("adding param: "+key+"="+val);
            try {
                url_val = URLEncoder.encode(val,"UTF-8");
            }
            catch(UnsupportedEncodingException uee) {
                log.warning("unable to URL encode "+key+": "+val);
            }
            //log.info("encoded val: "+url_val);
            body.append(key+"="+url_val+"&");
            //it.remove(); // avoids a ConcurrentModificationException
        }
        //remove last ampersand
        String postBody = (body.toString()).substring(0, body.length()-1);
        //log.info("postBody: "+postBody);
        return postBody;
    }


    public boolean getReport(String name, String type, String url, Date start, Date end, Map<String,String> params) {
        if(props == null) {
            loadProperties();
        }
        setXmlDoc(null);
        setResponse(null);

        boolean success = false;
        SimpleDateFormat format = new SimpleDateFormat(ISO_FORMAT);
        String startStr = format.format(start);
        String endStr = format.format(end);
        setReportParams(new HashMap<String,String>()); //clear report params

        String usr = props.getProperty(USER_KEY, "");
        String pwd = props.getProperty(PWD_KEY, "");
        String entityId = props.getProperty(ENTITY_ID_KEY, "");

        try {
            success = login(usr, pwd);
            if(success) {
                success = setEntity(entityId);
                if(success) {
                    String response = callApi(url,params);
                    setResponse(response);

                    /* load the XML */
                    Document responseDoc = createDoc(response);
                    if(responseDoc == null) {
                        log.severe("No response from API: "+url);
                        success = false;
                    }
                    else {
                        setXmlDoc(responseDoc);
                        success = successCheck(responseDoc);
                    }

                    params.put("reportType", type);
                    params.put("reportName", name);
                    params.put("reportUrl",url); 
                    params.put("reportSuccess",Boolean.toString(success));
                    setReportParams(params);
                        
                }
                logout();
            }
        }
        catch(JSONException jsonE) {
            log.severe("can't parse LiveRail response xml for "+type+" report "+name+":\n"+jsonE.getMessage());
        }
        catch(Exception reqEx) {
            log.severe("Could not get "+type+" report "+name+" from LiveRail:\n"+reqEx.getMessage());
        }
        
        return success;
    }

    public boolean getReport(String reportType, Date start, Date end) {

        if(reportType.equals(STAT_REPORT)) {
            return getStatisticsReport(start,end);
        }
        else if(reportType.equals(PLAT_REPORT)) {
            return getPlatformReport(start,end);
        }
        else if(reportType.equals(AGG_REPORT)) {
            getAggregatedReport(start,end);
        }

        return false;
    }

    public boolean getStatisticsReport(Date start, Date end) {
        SimpleDateFormat format = new SimpleDateFormat(ISO_FORMAT);
        String startStr = format.format(start);
        String endStr = format.format(end);
        Map<String,String> params = new LinkedHashMap<String,String>();
        params.put("time_start",startStr);
        params.put("time_end",endStr);
        params.put("timezone","-4");
        params.put("aggregation","all");
        //params.put("dimension1","order_id");
        params.put("dimension1","hour");
        params.put("dimension2","vertical_id");
        params.put("dimension3","publisher_id");
        //params.put("dimension4","domain_name");
        params.put("dimension4","creative_campaign_id");
        
        params.put("filters[metric]","impression");

        String url = "/statistics/";
        String name = "statistics_"+startStr.substring(0,10)+"_"+endStr.substring(0,10);

        return getReport(name, STAT_REPORT, url, start, end, params);
    }

    public boolean getAggregatedReport(Date start, Date end) {
        SimpleDateFormat format = new SimpleDateFormat(ISO_FORMAT);
        String startStr = format.format(start);
        String endStr = format.format(end);
        Map<String,String> params = new LinkedHashMap<String,String>();
        params.put("start",startStr);
        params.put("end",endStr);
        params.put("timezone","-4");
        params.put("dimensions","partner_id");
        params.put("metrics","inventory,impressions,revenue,spend");

        String url = "/statistics/aggregated/";
        String name = "aggstats_"+startStr.substring(0,10)+"_"+endStr.substring(0,10);
        return getReport(name, AGG_REPORT, url, start, end, params);
        
    }

    public boolean getPlatformReport(Date start, Date end) {
        SimpleDateFormat format = new SimpleDateFormat(ISO_FORMAT);
        String startStr = format.format(start);
        String endStr = format.format(end);
        Map<String,String> params = new LinkedHashMap<String,String>();
        params.put("time_start",startStr);
        params.put("time_end",endStr);
        params.put("timezone","-4");
        params.put("dimension1","month");
        params.put("dimension2","os_name");
        params.put("dimension3","vertical_id");
        params.put("dimension4","publisher_id");
        params.put("aggregation","all"); //default
        params.put("filters[metric]","impression");
        //params.put("dimension4","advertiser_id"); //not available through svc
        //params.put("selected_interval","last_month"); //not available through svc
        //params.put("columns","revenue"); //not available through svc

        String url = "/statistics/";
        String name = "platform_brand_advertiser_last_month_"+startStr.substring(0,10)+"_"+endStr.substring(0,10);
        return getReport(name, PLAT_REPORT, url, start, end, params);
        
    }
    
    private void loadProperties() {
        log.info("LiveRailClient loading properties . . .");
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("cn.properties");
            props = new Properties();
            props.load(is);
            /*
            String prop = (String)props.getProperty(PWD_KEY,null);
            if(prop != null) {
                log.info(PWD_KEY+" = "+prop);
                this.pwd = prop;
            }
            */
        }
        catch(IOException ie) {
            log.log(Level.SEVERE, "Could not load LiveRailClient properties: ", ie);
        }
        catch(Exception e) {
            log.log(Level.SEVERE, "problems loading LiveRailClient properties: ", e);
        }
    }

    public static void main(String[] args) {
        LiveRailClient liveRailClient = new LiveRailClient();
        //get today's date
        Date today = new Date();
        
        //create date minus one day
        Calendar cal = Calendar.getInstance();
        cal.setTime(today);
        cal.add(Calendar.DAY_OF_YEAR,-1);
        Date yesterday = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR,-7);
        Date weekPrevious = cal.getTime();


        //liveRailClient.getAggregatedReport(weekPrevious,yesterday);
        //liveRailClient.getStatisticsReport(weekPrevious,yesterday);
        liveRailClient.getPlatformReport(weekPrevious,yesterday);
    }
    

}