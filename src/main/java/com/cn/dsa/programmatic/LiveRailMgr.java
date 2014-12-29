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

import it.sauronsoftware.cron4j.Scheduler;

import com.mongodb.MongoClient;
import com.mongodb.DB;
import com.mongodb.WriteResult;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.bson.types.ObjectId;

import org.json.XML;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.binary.Base64;

import com.cn.dsa.db.MongoMgr;

public class LiveRailMgr {

    private static Logger log = Logger.getLogger(LiveRailMgr.class.getName());
    //example: 2014-07-01 00:00:00
    private static String ISO_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static String ISO_DATE = "yyyy-MM-dd";

    private static String MONGO_DB = "programmatic";
    private static String MONGO_COLLECTION = "liverail";


    private LiveRailClient liveRailClient = new LiveRailClient();
    
    private Scheduler scheduler = null;
    private static int PAUSE = 10; //seconds to wait between each report request.
    
    //mongoDB objects
    private MongoMgr mongoMgr = MongoMgr.getInstance();
    private MongoClient mongoClient;
    private DB mongoDb;
    private DBCollection dbCollection;

    private String chronStr = "30 6 * * *"; //once-a-day at 6:30am

    public void setChronStr(String chronStr) {
        this.chronStr = chronStr;
    }
    public String getChronStr() {
        return chronStr;
    }

    public LiveRailMgr() {
        super();
    }

    public LiveRailMgr(String chron) {
        super();
        setChronStr(chron);
        trackReports();
    }

    public void trackReports() {
        log.info("kicking off continuous tracking of LiveRail Programmatic Reports: "+getChronStr());
        // Creates a Scheduler instance.
        scheduler = new Scheduler();
        // Schedule a task.
        scheduler.schedule(getChronStr(), new Runnable() {
            public void run() {
                getAllReports();
            }
        });
        // Starts the scheduler.
        scheduler.start();

    }

    public void stop() {
        if(scheduler != null) {
            scheduler.stop();
        }
    }

    public boolean getAllReports() {
        //get today's date
        Date today = new Date();
        
        //create date minus one day
        Calendar cal = Calendar.getInstance();
        cal.setTime(today);
        cal.add(Calendar.DAY_OF_YEAR,-1);
        Date yesterday = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR,-1);
        Date dayBeforeYesterday = cal.getTime();

        //get all reports for this date range
        return getAllReports(dayBeforeYesterday, yesterday);
    }

    public boolean getAllReports(Date start, Date end) {
        boolean totalSuccess = true;
        SimpleDateFormat format = new SimpleDateFormat(ISO_FORMAT);
        String startStr = format.format(start);
        String endStr = format.format(end);
        
        try {
            for(String reportType : LiveRailClient.REPORT_TYPES) {

                //first check if report is already there
                if(checkForReport(reportType,startStr,endStr)) {
                    log.info("report found. Cancelling request for identical report.");
                    continue;
                }

                if(!liveRailClient.getReport(reportType, start, end)) {
                    totalSuccess = false;
                }
                else {
                    Map<String,String> params = liveRailClient.getReportParams();
                    String name = params.get("reportName");
                    String mongoId = saveReportToDB(params, liveRailClient.getResponse(), start, end);
                    if(mongoId != null) {
                        log.info(name+" report done. MongoID: "+mongoId);
                    }
                    else {
                        log.info("could not save "+name+" report to MongoID");
                        totalSuccess = false;
                    }
                    
                }
                log.info("Waiting "+PAUSE+" seconds before next LiveRail request . . . ");
                Thread.sleep(PAUSE * 1000);
            }
        } catch (Exception e) {
            log.warning("Error getting LiveRail reports: \n"+e.getMessage());
            totalSuccess = false;
        }

        return totalSuccess;
    }


    public boolean checkForReport(String reportType, String startStr, String endStr) {
        BasicDBObject query = new BasicDBObject();
        query.append("reportType",reportType).append("startStr",startStr).append("endStr",endStr);

        if(dbCollection == null) {
            loadMongoCollection();
        }
        DBObject doc = dbCollection.findOne(query);
        if(doc != null) {
            log.info("LiveRail report of type "+reportType+" found for "+startStr+" to "+endStr);
            return true;
        }
        else {
            log.info("No LiveRail report of type "+reportType+" found for "+startStr+" to "+endStr);
            
        }
        
        return false;
    }



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

    private Document xmlDoc = null;
    public void setXmlDoc(Document xmlDoc) {
        this.xmlDoc = xmlDoc;
    }
    public Document getXmlDoc() {
        return xmlDoc;
    }


    private String saveReportToDB(Map<String,String> metadata, String report, Date start, Date end) throws Exception {
        JSONObject reportObj = XML.toJSONObject(report);
        String jsonStr = reportObj.toString();
        log.info("JSON created from report:\n"+jsonStr);
        BasicDBObject dbReportObj = (BasicDBObject)JSON.parse(jsonStr);

        BasicDBObject dbObj = new BasicDBObject().append("report",dbReportObj);
        dbObj.append("startDate",start).append("endDate",end);
        SimpleDateFormat format = new SimpleDateFormat(ISO_FORMAT);
        String startStr = format.format(start);
        String endStr = format.format(end);
        dbObj.append("startStr",startStr).append("endStr",endStr);

        //add the metadata
        dbObj.append("added",new Date());
        Iterator it = metadata.entrySet().iterator();
        log.info("adding metadata to JSON object");
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            String key = (String)pairs.getKey();
            String val = (String)pairs.getValue();
            log.info(key+" = "+val);
            dbObj.append(key, val);
            it.remove(); // avoids a ConcurrentModificationException
        }
        
        if(dbCollection == null) {
            loadMongoCollection();
        }
        WriteResult result = dbCollection.insert(dbObj);
        ObjectId id = (ObjectId)dbObj.get( "_id" );
        return id.toHexString();

    }

    private void loadMongoCollection() {
        mongoClient = mongoMgr.getClient();
        mongoDb = mongoClient.getDB(MONGO_DB);
        dbCollection = mongoDb.getCollection(MONGO_COLLECTION);
    }
    

    public static void main(String[] args) {
        LiveRailMgr liveRailMgr = new LiveRailMgr();
        //liveRailMgr.getAllReports();
        String startStr = "2014-09-01 00:00:00";
        String endStr = "2014-09-30 23:59:59";
        try {
            SimpleDateFormat isoFormatter = new SimpleDateFormat(ISO_FORMAT);
            Date start = isoFormatter.parse(startStr);
            Date end = isoFormatter.parse(endStr);

            liveRailMgr.getAllReports(start, end);

        }
        catch(Exception e) {
            System.err.println("error getting report for "+startStr+" - "+endStr);
        }

        
    }
    

}