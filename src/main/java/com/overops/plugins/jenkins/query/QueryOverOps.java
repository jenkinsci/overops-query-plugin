/*
 * The MIT License
 *
 * Copyright (c) 2018, OverOps, Inc., Joe Offenberg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.overops.plugins.jenkins.query;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;

import com.overops.plugins.jenkins.query.ReportBuilder.QualityReport;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.util.regression.RegressionInput;
import com.takipi.api.client.util.view.ViewUtil;
import com.takipi.api.core.url.UrlClient.Operation;
import com.takipi.api.core.url.UrlClient.UrlClientObserver;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

public class QueryOverOps extends Recorder implements SimpleBuildStep {

	private static final String SEPERATOR = ",";
	private static boolean runRegressions = false;

	//General Settings
	private String applicationName;
	private String deploymentName;
	private final String serviceId;
	private final String regexFilter;
	private final boolean markUnstable;
	private final Integer printTopIssues;

	//Quality Gates
	private final JSONObject checkNewErrors;
	private boolean newEvents;
	
	private final JSONObject checkResurfacedErrors;
	private boolean resurfacedErrors;
	
	private final JSONObject checkVolumeErrors;
	private Integer maxErrorVolume;
	
	private final JSONObject checkUniqueErrors;
	private Integer maxUniqueErrors;
	
	private final JSONObject checkCriticalErrors;
	private String criticalExceptionTypes;
	
	private final JSONObject checkRegressionErrors;
	private String activeTimespan;
	private String baselineTimespan;
	private Integer minVolumeThreshold;
	private Double minErrorRateThreshold;
	private Double regressionDelta;
	private Double criticalRegressionDelta;
	private boolean applySeasonality;
	
	//Debugging Options
	private final boolean debug;

	@DataBoundConstructor
	public QueryOverOps(String applicationName, String deploymentName, String serviceId, String regexFilter, boolean markUnstable, Integer printTopIssues, 
			JSONObject checkNewErrors, boolean newEvents, JSONObject checkResurfacedErrors, boolean resurfacedErrors, 
			JSONObject checkVolumeErrors, Integer maxErrorVolume, JSONObject checkUniqueErrors, 
			Integer maxUniqueErrors, JSONObject checkCriticalErrors, String criticalExceptionTypes, JSONObject checkRegressionErrors, String activeTimespan, 
			String baselineTimespan, Double minErrorRateThreshold, Integer minVolumeThreshold, boolean applySeasonality, Double regressionDelta, Double criticalRegressionDelta,
			boolean debug) {

		this.applicationName = applicationName;
		this.deploymentName = deploymentName;
		this.serviceId = serviceId;
		this.regexFilter = regexFilter;
		this.markUnstable = markUnstable;
		this.printTopIssues = printTopIssues;
		
		this.checkNewErrors = checkNewErrors;
		parseNewErrors();
		
		this.checkResurfacedErrors = checkResurfacedErrors;
		parseResurfacedErrors();
		
		this.checkVolumeErrors = checkVolumeErrors;
		parseVolumeErrors();
		
		this.checkUniqueErrors = checkUniqueErrors;
		parseUniqueErrors();
		
		this.checkCriticalErrors = checkCriticalErrors;
		parseCriticalExceptionTypes();
		
		this.checkRegressionErrors = checkRegressionErrors;
		parseRegressionValue();
	
		this.debug = debug;
	}


	// getters() needed for config.jelly
	 
	public String getapplicationName() {
		return applicationName;
	}

	public String getdeploymentName() {
		return deploymentName;
	}

	public String getregexFilter() {
		return regexFilter;
	}

	public String getserviceId() {
		return serviceId;
	}

	public boolean getdebug() {
		return debug;
	}

	public JSONObject getcheckNewErrors() {
		return checkNewErrors;
	}
	
	public JSONObject getcheckResurfacedErrors() {
		return checkResurfacedErrors;
	}

	public boolean getnewEvents() {
		return newEvents;
	}

	public JSONObject getcheckUniqueErrors() {
		return checkUniqueErrors;
	}
	
	public Integer getmaxUniqueErrors() {
		return maxUniqueErrors;
	}

	public JSONObject getcheckVolumeErrors() {
		return checkVolumeErrors;
	}
	
	public Integer getmaxErrorVolume() {
		return maxErrorVolume;
	}

	public JSONObject getcheckCriticalErrors() {
		return checkCriticalErrors;
	}
	
	public JSONObject getcheckRegressionErrors() {
		return checkRegressionErrors;
	}
	
	public String getcriticalExceptionTypes() {
		return criticalExceptionTypes;
	}

	public String getactiveTimespan() {
		return activeTimespan;
	}

	public String getbaselineTimespan() {
		return baselineTimespan;
	}

	public Double getminErrorRateThreshold() {
		return minErrorRateThreshold;
	}

	public Double getcriticalRegressionDelta() {
		return criticalRegressionDelta;
	}

	public Integer getminVolumeThreshold() {
		return minVolumeThreshold;
	}

	public Double getregressionDelta() {
		return regressionDelta;
	}

	public boolean getapplySeasonality() {
		return applySeasonality;
	}

	public Integer getprintTopIssues() {
		return printTopIssues;
	}

	public boolean getmarkUnstable() {
		return markUnstable;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	private static boolean isResolved(String value) {
		boolean isVar = (value.startsWith("${") && (value.endsWith("}")));
		return !isVar;
	}

	private static Collection<String> parseArrayString(String value, PrintStream printStream, String name) {
		if ((value == null) || (value.isEmpty())) {
			return Collections.emptySet();
		}

		if (!isResolved(value)) {
			printStream.println("Value " + value + " is unresolved for " + name + ". Ignoring.");
			return Collections.emptySet();
		}

		Collection<String> result = Arrays.asList(value.trim().split(Pattern.quote(SEPERATOR)));

		return result;
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
				
		String apiHost = getDescriptor().getOverOpsURL();
		String apiKey = Secret.toString(getDescriptor().getOverOpsAPIKey());

		PrintStream printStream;

		//next rev need to be able to have generic logging of when we start, settings, etc.
		if (debug) {
			printStream = listener.getLogger();
		} else {
			printStream = null;
		}
		
		pauseForTheCause(printStream);
		
		//validate inputs first
		validateInputs(printStream);

		ApiClient apiClient = ApiClient.newBuilder().setHostname(apiHost).setApiKey(apiKey).build();
		
		if ((printStream != null) && (debug)) {
			apiClient.addObserver(new ApiClientObserver(printStream, debug));
		}
		
		SummarizedView allEventsView = ViewUtil.getServiceViewByName(apiClient, serviceId, "All Events");
	
		if (allEventsView == null) {
			throw new IllegalStateException(
					"Could not acquire ID for 'All Events'. Please check connection to " + apiHost);
		}
		
		RegressionInput input = setupRegressionData(run, allEventsView, listener, printStream);
		
		QualityReport report = ReportBuilder.execute(apiClient, input, maxErrorVolume, maxUniqueErrors,
				printTopIssues, regexFilter, newEvents, resurfacedErrors, runRegressions, markUnstable, printStream, debug);

		OverOpsBuildAction buildAction = new OverOpsBuildAction(report, run);
		run.addAction(buildAction);

		if ((markUnstable) && (report.getUnstable())) {
			run.setResult(Result.UNSTABLE);
		}
	}
	
	//setup the regression object
	private RegressionInput setupRegressionData(Run<?, ?> run, SummarizedView allEventsView, TaskListener listener, PrintStream printStream) 
			throws InterruptedException, IOException {
		
		RegressionInput input = new RegressionInput();
		input.serviceId = serviceId;
		input.viewId = allEventsView.id;
		String expandedAppName = run.getEnvironment(listener).expand(applicationName);
		String expandedDepName = run.getEnvironment(listener).expand(deploymentName);
		input.applictations = parseArrayString(expandedAppName, printStream, "Application Name");
		input.deployments = parseArrayString(expandedDepName, printStream, "Deployment Name");
		input.criticalExceptionTypes = parseArrayString(criticalExceptionTypes, printStream,
				"Critical Exception Types");	

		if (checkRegressionErrors != null) {
			runRegressions = true;
			input.activeTimespan = convertToMinutes(activeTimespan);
			input.baselineTime = baselineTimespan;
			input.baselineTimespan = convertToMinutes(baselineTimespan);
			input.minVolumeThreshold = minVolumeThreshold;
			input.minErrorRateThreshold = minErrorRateThreshold;
			input.regressionDelta = regressionDelta;
			input.criticalRegressionDelta = criticalRegressionDelta;
			input.applySeasonality = applySeasonality;
			input.validate();
		} else {
			runRegressions = false;
		}
		
		printInputs(printStream, input);

		return input;
	}
	
	//validate inputs
	private void validateInputs (PrintStream printStream) throws InterruptedException, IOException {
		String apiHost = getDescriptor().getOverOpsURL();
		String apiKey = Secret.toString(getDescriptor().getOverOpsAPIKey());

		if (apiHost == null) {
			throw new IllegalArgumentException("Missing host name");
		}

		if (apiKey == null) {
			throw new IllegalArgumentException("Missing api key");
		}
		
		//validate active and baseline time window
		if (checkRegressionErrors != null) {
			if (!activeTimespan.equalsIgnoreCase("0")) {
				if (convertToMinutes(activeTimespan) == 0) {
					throw new IllegalArgumentException("For Increasing Error Gate, the active timewindow currently set to: " + activeTimespan +  " is not properly formated. See help for format instructions.");
				}
			}
			if (!baselineTimespan.equalsIgnoreCase("0")) {
				if (convertToMinutes(baselineTimespan) == 0) {
					throw new IllegalArgumentException("For Increasing Error Gate, the baseline timewindow currently set to: " + baselineTimespan + " cannot be zero or is improperly formated. See help for format instructions.");
				}
			}
		}

		String serviceId;

		if ((this.serviceId != null) && (!this.serviceId.isEmpty())) {
			serviceId = this.serviceId;
		} else {
			serviceId = getDescriptor().getOverOpsSID();
		}

		if (serviceId == null) {
			throw new IllegalArgumentException("Missing environment Id");
		}

	}
	
	//convert input string (7d) to minutes
	private int convertToMinutes(String timeWindow) {
		
		if (timeWindow.toLowerCase().contains("d")) {
			Integer days = Integer.valueOf(timeWindow.substring(0, timeWindow.indexOf("d")));
			return days * 24 * 60;
		} else if (timeWindow.toLowerCase().contains("h")) {
			Integer hours = Integer.valueOf(timeWindow.substring(0, timeWindow.indexOf("h")));
			return hours * 60;
		} else if (timeWindow.toLowerCase().contains("m")) {
			return Integer.valueOf(timeWindow.substring(0, timeWindow.indexOf("m")));
		} 
		
		return 0;
	}

	private void printInputs(PrintStream printStream, RegressionInput input) {

		if (printStream != null) {
			printStream.println(input);

			printStream.println("Max unique errors  = " + maxUniqueErrors);
			printStream.println("Max error volume  = " + maxErrorVolume);
			printStream.println("Check new errors  = " + newEvents);
			printStream.println("Check resurfaced errors  = " + resurfacedErrors);

			String regexPrint;

			if (regexFilter != null) {
				regexPrint = regexFilter;
			} else {
				regexPrint = "";
			}

			printStream.println("Regex filter  = " + regexPrint);
		}
	}
	
	
	//sleep for 1 minute to ensure all data is in OverOps especially for short running unit tests
	private static void pauseForTheCause(PrintStream printStream) {
		if (printStream != null) {
			printStream.println("Build Step: Starting OverOps Quality Gate....");
		}
		try {
			Thread.sleep(60000);
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	protected static class ApiClientObserver implements UrlClientObserver {

		private final PrintStream printStream;
		private final boolean verbose;

		public ApiClientObserver(PrintStream printStream, boolean verbose) {
			this.printStream = printStream;
			this.verbose = verbose;
		}

		@Override
		public void observe(Operation operation, String url, String response, long time) {
			StringBuilder output = new StringBuilder();

			output.append(String.valueOf(operation));
			output.append(" took ");
			output.append(time / 1000);
			output.append("ms for ");
			output.append(url);

			if (verbose) {
				output.append(". Response: ");
				output.append(response);
			}

			printStream.println(output.toString());
		}
	}
	
	//parse the JSON object to get the criticalExceptionTypes value
	private void parseCriticalExceptionTypes  () {
		if (checkCriticalErrors != null && !checkCriticalErrors.isNullObject()) {
			String value = checkCriticalErrors.getString ("criticalExceptionTypes");
			this.criticalExceptionTypes = value;
        } else {
            this.criticalExceptionTypes = null;
        }
	}
	
	//parse the JSON object to get the maxErrorVolume value
	private void parseVolumeErrors  () {
		if (checkVolumeErrors != null && !checkVolumeErrors.isNullObject()) {
			String value = checkVolumeErrors.getString ("maxErrorVolume");
			if (value != null && !value.isEmpty()) {
				this.maxErrorVolume = Integer.valueOf(value);
			}
        } else {
            this.maxErrorVolume = 0;
        }
	}
	
	//parse the JSON object to get the maxUniqueVolume value
	private void parseUniqueErrors  () {
		if (checkUniqueErrors != null && !checkUniqueErrors.isNullObject()) {
			String value = checkUniqueErrors.getString ("maxUniqueErrors");
			if (value != null && !value.isEmpty()) {
				this.maxUniqueErrors = Integer.valueOf(value);
			}
        } else {
            this.maxUniqueErrors = 0;
        }
	}
	
	//parse the JSON object to get the checkNewErrors value
	private void parseNewErrors() {
		if (checkNewErrors != null && !checkNewErrors.isNullObject()) {
            this.newEvents = true;
        } else {
            this.newEvents = false;
        }
	}
	
	//parse the JSON object to get the checkResurfacedErrors value
	private void parseResurfacedErrors() {
		if (checkResurfacedErrors != null && !checkResurfacedErrors.isNullObject()) {
            this.resurfacedErrors = true;
        } else {
            this.resurfacedErrors = false;
        }
	}
	
	//parse the JSON object to get the checkRegressionErrors values
	private void parseRegressionValue  () {
		if (checkRegressionErrors != null && !checkRegressionErrors.isNullObject()) {
			String value = checkRegressionErrors.getString ("activeTimespan");
			if (value != null && !value.isEmpty()) {
				this.activeTimespan = value;
			}
			value = checkRegressionErrors.getString ("baselineTimespan");
			if (value != null && !value.isEmpty()) {
				this.baselineTimespan = value;
			}
			value = checkRegressionErrors.getString ("minErrorRateThreshold");
			if (value != null && !value.isEmpty()) {
				this.minErrorRateThreshold = Double.valueOf(value);
			}
			value = checkRegressionErrors.getString ("minVolumeThreshold");
			if (value != null && !value.isEmpty()) {
				this.minVolumeThreshold = Integer.valueOf(value);
			}
			this.applySeasonality = checkRegressionErrors.getBoolean ("applySeasonality");
			value = checkRegressionErrors.getString ("regressionDelta");
			if (value != null && !value.isEmpty()) {
				this.regressionDelta = Double.valueOf(value);
			}
			value = checkRegressionErrors.getString ("criticalRegressionDelta");
			if (value != null && !value.isEmpty()) {
				this.criticalRegressionDelta = Double.valueOf(value);
			}
        } else {
	        	this.activeTimespan = "0";
	    		this.baselineTimespan = "0";
	    		this.minErrorRateThreshold = 0d;
	    		this.minVolumeThreshold = 0;
	    		this.applySeasonality = false;
	    		this.regressionDelta = 0d;
	    		this.criticalRegressionDelta = 0d;
        }
	}
}
