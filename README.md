# OverOps Reliability Report - Jenkins Plugin

The plugin provides a mechanism for applying OverOps severity assignment and regression analysis to new builds.  Run this plugin as a post build step after all other testing is complete.     


## Installation
  Prerequisites

  * Jenkins running on Java 1.8 or later
  


## Global Configuration

  Select Manage Jenkins -> Configure Plugin 
  scroll down to **OverOps Query Plugin**
  
  **OverOps URL:**  The complete url including port of the OverOps e.g. http://localhost:8080 or for SaaS  https://api.overops.com
  
  **OverOps Service ID**  OverOps Service ID (begins with S)
  
  **OverOps API Key**  When using an API key User and Password fields are ignored.
  
Test connection would show you a count of available metrics.  If the count shows 0 measurements, credentials are correct but    database may be wrong.  If credentials are incorrect you will receive an authentication error.
  

## Job Post Build Configuration
  **Application Name**  OverOps Application Name to match in Query
  
  **Deployment Name**  OverOps Deployment Name to match in Query.  Can make use of Jenkins Build Variables such as ${BUILD_NUMBER}.

  **Active Time Window**  The time window (in minutes) inspected to seach for new issues and regressions.
  
  **Baseline Time Window**  The time window in minutes against which events in the active window are compared to test for regressions.
  
  **	Critical Exception types**  A comma delimited list of exception types that are deemed as severe regardless of their volume. 
  
  **Event Volume Threshold**  The minimal number of times an event of a non-critical type (e.g. uncaught) must take place to be considered severe.
  
  **	Error Rate Threshold**  The acceptable relative rate between instances of an event and calls into its code. A rate of 0.1 means the events is allowed to take place <= 10% of the time.
  
  **Regression Delta**  The change in percentage between an event's rate in the active time span compared to the baseline to be considered a regression.
  
  **	Critical Regression Threshold**  The change in percentage between an event's rate in the active time span compared to the baseline to be considered a critical regression.
(from OverOps Query Plugin)
  
  **Environment ID**  The OverOps environment identifier (e.g S4567) to inspect data for this build
  

  **Mark Build Unstable**  Check if we should mark the build unstable if the Max Record Count is exceeded.  

  **Show Query Results**  Check if we should display the query results in the Jenkins console.  Query results are depicted with UTC time stamps.
ß
