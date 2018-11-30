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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;

import com.overops.plugins.jenkins.query.RegressionReportBuilder.RegressionReport;
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

public class QueryOverOps extends Recorder implements SimpleBuildStep
{
	
	private static final String SEPERATOR = "'";
	
	private final int activeTimespan;
	private final int baselineTimespan;
	
	private final String criticalExceptionTypes;
	
	private final int minVolumeThreshold;
	private final double minErrorRateThreshold;
	
	private final double regressionDelta;
	private final double criticalRegressionDelta;
	
	private final boolean applySeasonality;
	
	private final int serverWait;
	
	private final boolean showResults;
	private final boolean verbose;
	
	private final int printTopIssues;
	private final int maxErrorVolume;
	private final int maxUniqueErrors;
	
	private final String serviceId;
	
	private final boolean markUnstable;
	private String applicationName;
	private String deploymentName;
	
	@DataBoundConstructor
	public QueryOverOps(String applicationName, String deploymentName,
			int activeTimespan, int baselineTimespan,
			String criticalExceptionTypes,
			int minVolumeThreshold, double minErrorRateThreshold,
			double regressionDelta, double criticalRegressionDelta,
			boolean applySeasonality, boolean markUnstable, boolean showResults,
			int printTopIssues, int maxErrorVolume, int maxUniqueErrors,
			boolean verbose, String serviceId,
			int serverWait)
	{
		
		this.serviceId = serviceId;
		
		this.applicationName = applicationName;
		this.deploymentName = deploymentName;
		this.criticalExceptionTypes = criticalExceptionTypes;
		
		this.activeTimespan = activeTimespan;
		this.baselineTimespan = baselineTimespan;
		
		this.minErrorRateThreshold = minErrorRateThreshold;
		this.minVolumeThreshold = minVolumeThreshold;
		
		this.applySeasonality = applySeasonality;
		this.regressionDelta = regressionDelta;
		this.criticalRegressionDelta = criticalRegressionDelta;
		
		this.maxErrorVolume = maxErrorVolume;
		this.maxUniqueErrors = maxUniqueErrors;
		
		this.printTopIssues = printTopIssues;
		
		this.serverWait = serverWait;
		this.verbose = verbose;
		this.showResults = showResults;
		this.markUnstable = markUnstable;
	}
	
	//getters() needed for config.jelly
	
	public String getapplicationName()
	{
		return applicationName;
	}
	
	public String getdeploymentName()
	{
		return deploymentName;
	}
	
	public int getactiveTimespan()
	{
		return activeTimespan;
	}
	
	public int getbaselineTimespan()
	{
		return baselineTimespan;
	}
	
	public double getminErrorRateThreshold()
	{
		return minErrorRateThreshold;
	}
	
	public double getminVolumeThreshold()
	{
		return minVolumeThreshold;
	}
	
	public double getregressionDelta()
	{
		return regressionDelta;
	}
	
	public String getcriticalExceptionTypes()
	{
		return criticalExceptionTypes;
	}
	
	public double getcriticalRegressionDelta()
	{
		return criticalRegressionDelta;
	}
	
	public String getserviceId()
	{
		return serviceId;
	}
	
	public int getserverWait()
	{
		return serverWait;
	}
	
	public boolean getverbose()
	{
		return verbose;
	}
	
	public boolean getshowResults()
	{
		return showResults;
	}
	
	public int getmaxErrorVolume()
	{
		return maxErrorVolume;
	}
	
	public int getmaxUniqueErrors()
	{
		return maxUniqueErrors;
	}
	
	public int getprintTopIssues()
	{
		return printTopIssues;
	}
	
	public boolean getmarkUnstable()
	{
		return markUnstable;
	}
	
	@Override
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.NONE;
	}
	
	@Override
	public DescriptorImpl getDescriptor()
	{
		return (DescriptorImpl)super.getDescriptor();
	}
	
	private static boolean isResolved(String value)
	{
		boolean isVar = (value.startsWith("${") && (value.endsWith("}")));
		return !isVar;
	}
	
	private static Collection<String> parseArrayString(String value, PrintStream printStream, String name)
	{
		
		if ((value == null) || (value.isEmpty()))
		{
			return Collections.emptySet();
		}
		
		if (!isResolved(value))
		{
			printStream.println("Value " + value + " is unresolved for " + name + ". Ignoring.");
			return Collections.emptySet();
		}
		
		Collection<String> result = Arrays.asList(value.trim().split(Pattern.quote(SEPERATOR)));
		
		return result;
	}
	
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException
	{
		
		String apiHost = getDescriptor().getOverOpsURL();
		String apiKey = Secret.toString(getDescriptor().getOverOpsAPIKey());
		
		if (apiHost == null)
		{
			throw new IllegalArgumentException("Missing host name");
		}
		
		if (apiKey == null)
		{
			throw new IllegalArgumentException("Missing api key");
		}
		
		String serviceId;
		
		if ((this.serviceId != null) && (!this.serviceId.isEmpty()))
		{
			serviceId = this.serviceId;
		}
		else
		{
			serviceId = getDescriptor().getOverOpsSID();
		}
		
		if (serviceId == null)
		{
			throw new IllegalArgumentException("Missing environment Id");
		}
		
		PrintStream printStream;
		
		if (showResults)
		{
			printStream = listener.getLogger();
		}
		else
		{
			printStream = null;
		}
		
		ApiClient apiClient = ApiClient.newBuilder().setHostname(apiHost).setApiKey(apiKey).build();
		
		if ((printStream != null) && (showResults)) {
			apiClient.addObserver(new ApiClientObserver(printStream, verbose));
		}
		
		SummarizedView allEventsView = ViewUtil.getServiceViewByName(apiClient, serviceId, "All Events");
		
		if (allEventsView == null)
		{
			throw new IllegalStateException(
					"Could not acquire ID for 'All Events'. Please check connection to " + apiHost);
		}
		
		if (serverWait > 0)
		{
			
			if ((showResults) && (printStream != null))
			{
				printStream.println("Waiting " + serverWait + " seconds for code analysis to complete");
			}
			
			TimeUnit.SECONDS.sleep(serverWait);
		}
		
		RegressionInput input = new RegressionInput();
		
		input.serviceId = serviceId;
		input.viewId = allEventsView.id;
		input.activeTimespan = activeTimespan;
		input.baselineTimespan = baselineTimespan;
		input.minVolumeThreshold = minVolumeThreshold;
		input.minErrorRateThreshold = minErrorRateThreshold;
		input.regressionDelta = regressionDelta;
		input.criticalRegressionDelta = criticalRegressionDelta;
		input.applySeasonality = applySeasonality;
		
		String expandedAppName = run.getEnvironment(listener).expand(applicationName);
		String expandedDepName = run.getEnvironment(listener).expand(deploymentName);
		
		input.criticalExceptionTypes =
				parseArrayString(criticalExceptionTypes, printStream, "Critical Exception Types");
		input.criticalExceptionTypes =
				parseArrayString(criticalExceptionTypes, printStream, "Critical Exception Types");
		input.applictations = parseArrayString(expandedAppName, printStream, "Application Name");
		input.deployments = parseArrayString(expandedDepName, printStream, "Deployment Name");
		
		input.validate();
		
		RegressionReport report = RegressionReportBuilder.execute(apiClient, 
			input, maxErrorVolume, maxUniqueErrors, printTopIssues, printStream, verbose);
		
		OverOpsBuildAction buildAction = new OverOpsBuildAction(report, run);
		run.addAction(buildAction);
		
		if ((markUnstable) && (report.getUnstable()))
		{
			run.setResult(Result.UNSTABLE);
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
		public void observe(Operation operation, String url, String response, long time)
		{
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
