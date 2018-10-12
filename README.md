# OverOps Query Jenkins Plugin

This plugin produces a report of all newly introduced issues as well as volume regressions (i.e. events who are now happening at a higher frequency than before) introduced into your integration / production environments(s) .  Run this plugin as a post build step after all other testing is complete.     


## Installation
  Prerequisites

  * Jenkins running on Java 1.8 or later
  


## Global Configuration

  Select Manage Jenkins -> Configure Plugin 
  scroll down to **OverOps Query Plugin**
  
  **OverOps URL:**  The complete url including port of the OverOps e.g. http://localhost:8080 or for SaaS  https://api.overops.com
  
  **OverOps Environment ID**  a default OverOps Environment ID (e.g S1234) in which to search for issues. Can be overridden in the build config. 
  
  
  **OverOps API Key**  An OverOps API key (can be obtained from your OverOps Settings | Account screen) used for authentication.
    

## Job Post Build Configuration 
  **Active Time Window**  The time window calculated as [from: now() - <active window>, to: now()] inspected to search for new issues and regressions.For example, a value of 1440 (= 24 * 60) means compare the last day against the baseline time window preceding it. 
  
  **Baseline Time Window**  The time window in minutes used to calculate the baseline time window for regressi0ons analysis. Calculated as: [from: now() - <active + baseline window>, to: now() - <active window>]. For example, a value of 10080 (= 24 * 60 * 7) means compare the active time window against the week preceding it.
  
  **Critical Exception types**  A comma delimited list of exception types that are deemed as severe regardless of their volume. Default values are: NullPointerException,IndexOutOfBoundsException,ClassCastException,AssertionError,ArithmeticException.
  
  **Event Volume Threshold**  The minimal number of times an event of a non-critical type (e.g. uncaught exception or critical exception type) must take place to be considered severe. For example, a value of 100 means an event must happen more than a 100 times in the active time window to be considered a possible regression or severe new issue.
  
  **Error Rate Threshold**  The acceptable relative rate between instances of an event and calls into its code. For example, an event taking place 25 out of 500 calls into the code is said to have a rate of 0.05.If its value in the active time window is below than specified in the error rate threshold, it will not be considered a candidate for regression analysis.
  
  **Regression Delta**  The change in rate between an event's rate in the active time span and the error rate during the baseline window to be considered a critical regression that can be used to mark the build as unstable. For example, for an event that used to happen 250 of 5,000 calls into the code (rate = 0.01) in the baseline time window and is now happening 2000 out of 5,000 calls (rate = 0.4), a supplied value of > 0.35 will mark the event as a critical regression.   

  **Environment ID**  The OverOps environment identifier (e.g S4567) to retrieve data from.

  **Apply seasonality**  For events marked as regression tests to see that any preceding time window within the baseline window did not exceed the volume within the active time window, or that two previous windows did not exceed for each 50% of the active volume. See more: https://en.wikipedia.org/wiki/Seasonality. 
  
  For example, an event whose volume is 1000 on Monday may be considered a regression if within the preceding baseline window of a week it only happened 2000 times. The seasonality check will see whether or not within any one of the preceding seasonal windows (calculated as the baseline / active, which in this example translates to a seasonal window of day) the volume has exceeded that of Monday. So if the preceding Tuesday saw 1500 events with the remaining 500 spread across the week, the event will not be considered as a regression, but as a "normal" seasonal occurrence. 
  
  The same would be true if the preceding Thursday and Saturday for example saw 500 events each (50% of the active volume). In this case the event will also be considered a "seasonal" spike vs. a regression.
  
  **Mark Build Unstable**  Check if we should mark the build unstable if a critical regression or new issue is discovered.
 
  **Verbose**  Print information around each event within the active time window that is inspected for regression analysis. 
    
  **Show Query Results**  Check if we should display the query results in the Jenkins console.  Query results are depicted with UTC time stamps.
ß