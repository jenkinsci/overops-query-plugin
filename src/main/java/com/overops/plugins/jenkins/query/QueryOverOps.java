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
import org.kohsuke.stapler.DataBoundSetter;

import com.overops.plugins.jenkins.query.ReportBuilder.QualityReport;
import com.takipi.api.client.RemoteApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.observe.Observer;
import com.takipi.api.client.util.regression.RegressionInput;
import com.takipi.api.client.util.view.ViewUtil;

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
	private boolean runRegressions = false;

	//General Settings
	private String applicationName;
	private String deploymentName;
	private String serviceId;
	private String url;
	private String apiToken;
	private String regexFilter;
	private boolean markUnstable;
	private Integer printTopIssues;

	//Quality Gates
	private JSONObject checkNewErrors;
	private boolean newEvents;

	private JSONObject checkResurfacedErrors;
	private boolean resurfacedErrors;

	private JSONObject checkVolumeErrors;
	private Integer maxErrorVolume;

	private JSONObject checkUniqueErrors;
	private Integer maxUniqueErrors;

	private JSONObject checkCriticalErrors;
	private String criticalExceptionTypes;

	private JSONObject checkRegressionErrors;
	private String activeTimespan;
	private String baselineTimespan;
	private Integer minVolumeThreshold;
	private Double minErrorRateThreshold;
	private Double regressionDelta;
	private Double criticalRegressionDelta;
	private boolean applySeasonality;

	// Advanced Options
	private boolean debug;
	private boolean errorSuccess;

	// all settings are optional
	@DataBoundConstructor
	public QueryOverOps() {
		// defaults
		this.applicationName = null;
		this.deploymentName = null;
		this.serviceId = null;
		this.regexFilter = null;
		this.markUnstable = false;
		this.printTopIssues = 5;

		this.checkNewErrors = null;
		this.newEvents = false;

		this.checkResurfacedErrors = null;
		this.resurfacedErrors = false;

		this.checkVolumeErrors = null;
		this.maxErrorVolume = 0;

		this.checkUniqueErrors = null;
		this.maxUniqueErrors = 0;

		this.checkCriticalErrors = null;
		this.criticalExceptionTypes = null;

		this.checkRegressionErrors = null;
		this.activeTimespan = "0";
		this.baselineTimespan = "0";
		this.minErrorRateThreshold = 0d;
		this.minVolumeThreshold = 0;
		this.applySeasonality = false;
		this.regressionDelta = 0d;
		this.criticalRegressionDelta = 0d;

		this.debug = false;
		this.errorSuccess = false;
	}

	// deprecated for improved Pipeline integration - see: https://jenkins.io/doc/developer/plugin-development/pipeline-integration/#constructor-vs-setters
	@Deprecated
	public QueryOverOps(String applicationName, String deploymentName, String serviceId, String regexFilter, boolean markUnstable, Integer printTopIssues, 
			JSONObject checkNewErrors, boolean newEvents, JSONObject checkResurfacedErrors, boolean resurfacedErrors, 
			JSONObject checkVolumeErrors, Integer maxErrorVolume, JSONObject checkUniqueErrors, 
			Integer maxUniqueErrors, JSONObject checkCriticalErrors, String criticalExceptionTypes, JSONObject checkRegressionErrors, String activeTimespan, 
			String baselineTimespan, Double minErrorRateThreshold, Integer minVolumeThreshold, boolean applySeasonality, Double regressionDelta, Double criticalRegressionDelta,
			boolean debug, boolean errorSuccess) {

		setApplicationName(applicationName);
		setDeploymentName(deploymentName);
		setServiceId(serviceId);

		setRegexFilter(regexFilter);
		setMarkUnstable(markUnstable);
		setPrintTopIssues(printTopIssues);

		setCheckNewErrors(checkNewErrors);
		setCheckResurfacedErrors(checkResurfacedErrors);
		setCheckVolumeErrors(checkVolumeErrors);
		setCheckUniqueErrors(checkUniqueErrors);

		setCheckCriticalErrors(checkCriticalErrors);

		setCheckRegressionErrors(checkRegressionErrors);

		setDebug(debug);
		setErrorSuccess(errorSuccess);
	}

	// getters() needed for config.jelly and Pipeline
	// setters for Pipeline

	public String getApplicationName() {
		return applicationName;
	}

	@DataBoundSetter
	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public String getDeploymentName() {
		return deploymentName;
	}

	@DataBoundSetter
	public void setDeploymentName(String deploymentName) {
		this.deploymentName = deploymentName;
	}

	public String getRegexFilter() {
		return regexFilter;
	}

	@DataBoundSetter
	public void setRegexFilter(String regexFilter) {
		this.regexFilter = regexFilter;
	}

	public String getServiceId() {
		return serviceId;
	}

	@DataBoundSetter
	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public boolean getDebug() {
		return debug;
	}

	@DataBoundSetter
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public boolean getErrorSuccess() {
		return errorSuccess;
	}

	@DataBoundSetter
	public void setErrorSuccess(boolean errorSuccess) {
		this.errorSuccess = errorSuccess;
	}

	public JSONObject getCheckNewErrors() {
		return checkNewErrors;
	}

	@DataBoundSetter
	public void setCheckNewErrors(JSONObject checkNewErrors) {
		this.checkNewErrors = checkNewErrors;

		// parse JSON object to get the newEvents value
		if (checkNewErrors != null && !checkNewErrors.isNullObject()) {
			setNewEvents(checkNewErrors.getBoolean("newEvents"));
		}
	}

	public JSONObject getCheckResurfacedErrors() {
		return checkResurfacedErrors;
	}

	@DataBoundSetter
	public void setCheckResurfacedErrors(JSONObject checkResurfacedErrors) {
		this.checkResurfacedErrors = checkResurfacedErrors;

		// parse JSON object to get the resurfacedErrors value
		if (checkResurfacedErrors != null && !checkResurfacedErrors.isNullObject()) {
			setResurfacedErrors(checkResurfacedErrors.getBoolean("resurfacedErrors"));
		}
	}

	public boolean getResurfacedErrors() {
		return resurfacedErrors;
	}

	@DataBoundSetter
	public void setResurfacedErrors(boolean resurfacedErrors) {
		this.resurfacedErrors = resurfacedErrors;
	}

	public boolean getNewEvents() {
		return newEvents;
	}

	@DataBoundSetter
	public void setNewEvents(boolean newEvents) {
		this.newEvents = newEvents;
	}

	public JSONObject getCheckUniqueErrors() {
		return checkUniqueErrors;
	}

	@DataBoundSetter
	public void setCheckUniqueErrors(JSONObject checkUniqueErrors) {
		this.checkUniqueErrors = checkUniqueErrors;

		//parse the JSON object to get the maxUniqueErrors value
		if (checkUniqueErrors != null && !checkUniqueErrors.isNullObject()) {
			String value = checkUniqueErrors.getString("maxUniqueErrors");
			if (value != null && !value.isEmpty()) {
				setMaxUniqueErrors(Integer.valueOf(value));
			}
		}
	}

	public Integer getMaxUniqueErrors() {
		return maxUniqueErrors;
	}

	@DataBoundSetter
	public void setMaxUniqueErrors(Integer maxUniqueErrors) {
		this.maxUniqueErrors = maxUniqueErrors;
	}

	public JSONObject getCheckVolumeErrors() {
		return checkVolumeErrors;
	}

	@DataBoundSetter
	public void setCheckVolumeErrors(JSONObject checkVolumeErrors) {
		this.checkVolumeErrors = checkVolumeErrors;

		// parse JSON object to get maxErrorVolume value
		if (checkVolumeErrors != null && !checkVolumeErrors.isNullObject()) {
			String value = checkVolumeErrors.getString("maxErrorVolume");
			if (value != null && !value.isEmpty()) {
				setMaxErrorVolume(Integer.valueOf(value));
			}
		}
	}

	public Integer getMaxErrorVolume() {
		return maxErrorVolume;
	}

	@DataBoundSetter
	public void setMaxErrorVolume(Integer maxErrorVolume) {
		this.maxErrorVolume = maxErrorVolume;
	}

	public JSONObject getCheckCriticalErrors() {
		return checkCriticalErrors;
	}

	@DataBoundSetter
	public void setCheckCriticalErrors(JSONObject checkCriticalErrors) {
		this.checkCriticalErrors = checkCriticalErrors;

		// parse the JSON object to get the criticalExceptionTypes value
		if (checkCriticalErrors != null && !checkCriticalErrors.isNullObject()) {
			String value = checkCriticalErrors.getString("criticalExceptionTypes");
			setCriticalExceptionTypes(value);
		}

	}

	public JSONObject getCheckRegressionErrors() {
		return checkRegressionErrors;
	}

	@DataBoundSetter
	public void setCheckRegressionErrors(JSONObject checkRegressionErrors) {
		this.checkRegressionErrors = checkRegressionErrors;

		//parse the JSON object to get the checkRegressionErrors values
		if (checkRegressionErrors != null && !checkRegressionErrors.isNullObject()) {
			String value = checkRegressionErrors.getString ("activeTimespan");
			if (value != null && !value.isEmpty()) {
				setActiveTimespan(value);
			}

			value = checkRegressionErrors.getString ("baselineTimespan");
			if (value != null && !value.isEmpty()) {
				setBaselineTimespan(value);
			}

			value = checkRegressionErrors.getString ("minErrorRateThreshold");
			if (value != null && !value.isEmpty()) {
				setMinErrorRateThreshold(Double.valueOf(value));
			}

			value = checkRegressionErrors.getString ("minVolumeThreshold");
			if (value != null && !value.isEmpty()) {
				setMinVolumeThreshold(Integer.valueOf(value));
			}

			setApplySeasonality(checkRegressionErrors.getBoolean("applySeasonality"));

			value = checkRegressionErrors.getString ("regressionDelta");
			if (value != null && !value.isEmpty()) {
				setRegressionDelta(Double.valueOf(value));
			}

			value = checkRegressionErrors.getString ("criticalRegressionDelta");
			if (value != null && !value.isEmpty()) {
				setCriticalRegressionDelta(Double.valueOf(value));
			}
		}

	}

	public String getCriticalExceptionTypes() {
		return criticalExceptionTypes;
	}

	@DataBoundSetter
	public void setCriticalExceptionTypes(String criticalExceptionTypes) {
		this.criticalExceptionTypes = criticalExceptionTypes;
	}

	public String getActiveTimespan() {
		return activeTimespan;
	}

	@DataBoundSetter
	public void setActiveTimespan(String activeTimespan) {
		this.activeTimespan = activeTimespan;
	}

	public String getBaselineTimespan() {
		return baselineTimespan;
	}

	@DataBoundSetter
	public void setBaselineTimespan(String baselineTimespan) {
		this.baselineTimespan = baselineTimespan;

		// default is 0, but must be > 0. this must be set to run regressions
		if (convertToMinutes(baselineTimespan) > 0) {
			runRegressions = true;
		}
	}

	public Double getMinErrorRateThreshold() {
		return minErrorRateThreshold;
	}

	@DataBoundSetter
	public void setMinErrorRateThreshold(Double minErrorRateThreshold) {
		this.minErrorRateThreshold = minErrorRateThreshold;
	}

	public Double getCriticalRegressionDelta() {
		return criticalRegressionDelta;
	}

	@DataBoundSetter
	public void setCriticalRegressionDelta(Double criticalRegressionDelta) {
		this.criticalRegressionDelta = criticalRegressionDelta;
	}

	public Integer getMinVolumeThreshold() {
		return minVolumeThreshold;
	}

	@DataBoundSetter
	public void setMinVolumeThreshold(Integer minVolumeThreshold) {
		this.minVolumeThreshold = minVolumeThreshold;
	}

	public Double getRegressionDelta() {
		return regressionDelta;
	}

	@DataBoundSetter
	public void setRegressionDelta(Double regressionDelta) {
		this.regressionDelta = regressionDelta;
	}

	public boolean getApplySeasonality() {
		return applySeasonality;
	}

	@DataBoundSetter
	public void setApplySeasonality(boolean applySeasonality) {
		this.applySeasonality = applySeasonality;
	}

	public Integer getPrintTopIssues() {
		return printTopIssues;
	}

	@DataBoundSetter
	public void setPrintTopIssues(Integer printTopIssues) {
		this.printTopIssues = printTopIssues;
	}

	public boolean getMarkUnstable() {
		return markUnstable;
	}

	@DataBoundSetter
	public void setMarkUnstable(boolean markUnstable) {
		this.markUnstable = markUnstable;
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

		try {
			RemoteApiClient apiClient = (RemoteApiClient) RemoteApiClient.newBuilder().setHostname(apiHost).setApiKey(apiKey).build();

			if ((printStream != null) && (debug)) {
				apiClient.addObserver(new ApiClientObserver(printStream, debug));
			}

			SummarizedView allEventsView = ViewUtil.getServiceViewByName(apiClient, serviceId.toUpperCase(), "All Events");

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

		} catch (Exception ex) {
			// show exception in the UI

			OverOpsBuildAction buildAction = new OverOpsBuildAction(ex, run);
			run.addAction(buildAction);

			// mark build "not built" (or "success", set in advanced settings)
			if (errorSuccess) {
				run.setResult(Result.SUCCESS);
			} else {
				run.setResult(Result.NOT_BUILT);
			}
		}
	}

	@Override
	public String toString() {
		return "QueryOverOps[ " +
			"applicationName=" + this.applicationName + ", " +
			"deploymentName=" + this.deploymentName + ", " +
			"serviceId=" + this.serviceId + ", " +
			"regexFilter=" + this.regexFilter + ", " +
			"markUnstable=" + this.markUnstable + ", " +
			"printTopIssues=" + this.printTopIssues + ", " +
			"checkNewErrors=" + this.checkNewErrors + ", " +
			"newEvents=" + this.newEvents + ", " +
			"checkResurfacedErrors=" + this.checkResurfacedErrors + ", " +
			"resurfacedErrors=" + this.resurfacedErrors + ", " +
			"checkVolumeErrors=" + this.checkVolumeErrors + ", " +
			"maxErrorVolume=" + this.maxErrorVolume + ", " +
			"checkUniqueErrors=" + this.checkUniqueErrors + ", " +
			"maxUniqueErrors=" + this.maxUniqueErrors + ", " +
			"checkCriticalErrors=" + this.checkCriticalErrors + ", " +
			"criticalExceptionTypes=" + this.criticalExceptionTypes + ", " +
			"checkRegressionErrors=" + this.checkRegressionErrors + ", " +
			"activeTimespan=" + this.activeTimespan + ", " +
			"baselineTimespan=" + this.baselineTimespan + ", " +
			"minVolumeThreshold=" + this.minVolumeThreshold + ", " +
			"minErrorRateThreshold=" + this.minErrorRateThreshold + ", " +
			"regressionDelta=" + this.regressionDelta + ", " +
			"criticalRegressionDelta=" + this.criticalRegressionDelta + ", " +
			"applySeasonality=" + this.applySeasonality + ", " +
			"debug=" + this.debug + " ]";
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

		if (runRegressions) {
			input.activeTimespan = convertToMinutes(activeTimespan);
			input.baselineTime = baselineTimespan;
			input.baselineTimespan = convertToMinutes(baselineTimespan);
			input.minVolumeThreshold = minVolumeThreshold;
			input.minErrorRateThreshold = minErrorRateThreshold;
			input.regressionDelta = regressionDelta;
			input.criticalRegressionDelta = criticalRegressionDelta;
			input.applySeasonality = applySeasonality;
			input.validate();
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

		if ((this.serviceId == null) || (this.serviceId.isEmpty())) {
			this.serviceId = getDescriptor().getOverOpsSID();
		} 

		if (this.serviceId == null) {
			throw new IllegalArgumentException("Missing environment Id");
		}
		
		this.serviceId = this.serviceId.toUpperCase();

	}
	
	//convert input string (7d) to minutes
	private int convertToMinutes(String timeWindow) {
		
		if (timeWindow.toLowerCase().contains("d")) {
			Integer days = Integer.parseInt(timeWindow.substring(0, timeWindow.indexOf("d")));
			return days * 24 * 60;
		} else if (timeWindow.toLowerCase().contains("h")) {
			Integer hours = Integer.parseInt(timeWindow.substring(0, timeWindow.indexOf("h")));
			return hours * 60;
		} else if (timeWindow.toLowerCase().contains("m")) {
			return Integer.parseInt(timeWindow.substring(0, timeWindow.indexOf("m")));
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

	protected static class ApiClientObserver implements Observer {

		private final PrintStream printStream;
		private final boolean verbose;

		public ApiClientObserver(PrintStream printStream, boolean verbose) {
			this.printStream = printStream;
			this.verbose = verbose;
		}

		@Override
		public void observe(Operation operation, String url, String request, String response, int responseCode, long time) {
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

}
