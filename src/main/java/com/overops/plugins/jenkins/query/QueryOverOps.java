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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.overops.report.service.QualityReportParams;
import com.overops.report.service.ReportService;
import com.overops.report.service.ReportService.Requestor;
import com.overops.report.service.model.QualityReport;
import com.overops.report.service.model.QualityReportExceptionDetails;
import com.overops.report.service.model.QualityReport.ReportStatus;
import com.takipi.api.client.observe.Observer;

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

	//General Settings
	private String applicationName;
	private String deploymentName;
	private String serviceId;
	private String regexFilter;
	private boolean markUnstable;
	private Integer printTopIssues;
	private boolean showPassedGateEvents;


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
		this.showPassedGateEvents = false;
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
	public QueryOverOps(String applicationName, String deploymentName, String serviceId, String regexFilter, boolean markUnstable, boolean showPassedGateEvents, Integer printTopIssues, 
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
		setShowPassedGateEvents(showPassedGateEvents);
		setPrintTopIssues(printTopIssues);

		setCheckNewErrors(checkNewErrors);
		setCheckResurfacedErrors(checkResurfacedErrors);
		setCheckVolumeErrors(checkVolumeErrors);
		setCheckUniqueErrors(checkUniqueErrors);

		setCheckCriticalErrors(checkCriticalErrors);

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


	public String getCriticalExceptionTypes() {
		return criticalExceptionTypes;
	}

	@DataBoundSetter
	public void setCriticalExceptionTypes(String criticalExceptionTypes) {
		this.criticalExceptionTypes = criticalExceptionTypes;
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

	public boolean getShowPassedGateEvents() {
		return showPassedGateEvents;
	}

	@DataBoundSetter
	public void setShowPassedGateEvents(boolean showPassedGateEvents) {
		this.showPassedGateEvents = showPassedGateEvents;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
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
		
		//check to see if anything prior has failed and if so, skip the OverOps Quality Check
		Result result = run.getResult();
		if (result != null && result.isWorseThan(Result.UNSTABLE)) {
			printStream.println("Skipping OverOps Report due to prior build failure");
			return;
		}
		
		pauseForTheCause(printStream);

        QualityReport reportModel = null;
        ReportService reportService = new ReportService();
        try {
			validateInputs(printStream);
			QualityReportParams query = getQualityReportParams();
			reportModel = reportService.runQualityReport(apiHost, apiKey, query, Requestor.JENKINS, printStream, debug);
			OverOpsBuildAction buildAction = new OverOpsBuildAction(reportModel.getHtmlParts(showPassedGateEvents), run);
			run.addAction(buildAction);
			if (reportModel.getStatusCode() == ReportStatus.FAILED) {
				if ((reportModel.getExceptionDetails() != null) && errorSuccess) {
					run.setResult(Result.SUCCESS);
				} else {
					run.setResult(Result.UNSTABLE);
				}
			} else {
				run.setResult(Result.SUCCESS);
			}
        } catch (Exception exception) {
            reportModel = new QualityReport();

            QualityReportExceptionDetails exceptionDetails = new QualityReportExceptionDetails();
            exceptionDetails.setExceptionMessage(exception.getMessage());

            List<StackTraceElement> stackElements = Arrays.asList(exception.getStackTrace());
            List<String> stackTrace = new ArrayList<>();
            stackTrace.add(exception.getClass().getName());
            stackTrace.addAll(stackElements.stream().map(stack -> stack.toString()).collect(Collectors.toList()));
            exceptionDetails.setStackTrace(stackTrace.toArray(new String[stackTrace.size()]));

			reportModel.setExceptionDetails(exceptionDetails);

			OverOpsBuildAction buildAction = new OverOpsBuildAction(reportModel.getHtmlParts(), run);
			run.addAction(buildAction);

			if (errorSuccess) {
				run.setResult(Result.SUCCESS);
			} else {
				run.setResult(Result.UNSTABLE);
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

	private QualityReportParams getQualityReportParams() {
	
        QualityReportParams queryOverOps = new QualityReportParams();
        queryOverOps.setApplicationName(applicationName);
        queryOverOps.setDeploymentName(deploymentName);
        queryOverOps.setServiceId(serviceId);
        queryOverOps.setRegexFilter(regexFilter);
        queryOverOps.setMarkUnstable(markUnstable);
        queryOverOps.setPrintTopIssues(printTopIssues);
        queryOverOps.setNewEvents(newEvents);
		queryOverOps.setResurfacedErrors(resurfacedErrors);
		if (checkVolumeErrors != null && !checkVolumeErrors.isNullObject()) {
			String value = checkVolumeErrors.getString("maxErrorVolume");
			if (value != null && !value.isEmpty()) {
				queryOverOps.setMaxErrorVolume(Math.max(1, Integer.valueOf(value)));
			} else {
				queryOverOps.setMaxErrorVolume(1);
			}
		} else {
			queryOverOps.setMaxErrorVolume(0);
		}

		if (checkUniqueErrors != null && !checkUniqueErrors.isNullObject()) {
			String value = checkUniqueErrors.getString("maxUniqueErrors");
			if (value != null && !value.isEmpty()) {
				queryOverOps.setMaxUniqueErrors(Math.max(1, Integer.valueOf(value)));
			} else {
				queryOverOps.setMaxUniqueErrors(1);
			}
		} else {
			queryOverOps.setMaxUniqueErrors(0);
		}

		if (checkCriticalErrors != null && !checkCriticalErrors.isNullObject()) {
			String value = checkCriticalErrors.getString("criticalExceptionTypes");
			if (value != null && !value.isEmpty()) {
				queryOverOps.setCriticalExceptionTypes(value);
			} else {
				queryOverOps.setCriticalExceptionTypes("");
			}
		}

        queryOverOps.setActiveTimespan("0");
        queryOverOps.setBaselineTimespan("0");
        queryOverOps.setMinVolumeThreshold(0);
        queryOverOps.setMinErrorRateThreshold(0);
        queryOverOps.setRegressionDelta(0);
        queryOverOps.setCriticalRegressionDelta(0);
        queryOverOps.setApplySeasonality(false);

        return queryOverOps;
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
		

		if ((this.serviceId == null) || (this.serviceId.isEmpty())) {
			this.serviceId = getDescriptor().getOverOpsSID();
		} 

		if (this.serviceId == null) {
			throw new IllegalArgumentException("Missing environment Id");
		}
		
		this.serviceId = this.serviceId.toUpperCase();

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
