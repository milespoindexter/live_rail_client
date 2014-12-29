Java client for automated report retrieval from LiveRail API.

2014-11-21<br>
Miles Poindexter<br>
selfpropelledcity@gmail.com<br>

This client is designed to run as a service inside Apache ServiceMix (SMX).<br>
http://servicemix.apache.org/

It has also been tested for Talend ESB, which is based in SMX.<br>
https://www.talend.com/products/esb

TEST:<br>
mvn exec:java -Dexec.mainClass="com.cn.dsa.programmatic.LiveRailMgr"

mvn exec:java -Dexec.mainClass="com.cn.dsa.programmatic.LiveRailClient"