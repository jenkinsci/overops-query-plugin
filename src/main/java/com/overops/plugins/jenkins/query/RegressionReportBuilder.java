package com.overops.plugins.jenkins.query;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.util.event.EventUtil;
import com.takipi.api.client.util.regression.RateRegression;
import com.takipi.api.client.util.regression.RegressionInput;
import com.takipi.api.client.util.regression.RegressionResult;
import com.takipi.api.client.util.regression.RegressionStringUtil;
import com.takipi.api.client.util.regression.RegressionUtil;

public class RegressionReportBuilder {
	
	public static class RegressionReport {

		private final List<OOReportEvent> newIssues;
		private final List<OOReportRegressedEvent> regressions;
		private final List<OOReportEvent> allIssues;
		private final boolean unstable;

		RegressionReport(List<OOReportEvent> newIssues, List<OOReportRegressedEvent> regressions, boolean unstable) {
			this.newIssues = newIssues;
			this.regressions = regressions;
			this.allIssues = new ArrayList<OOReportEvent>();
			
			allIssues.addAll(newIssues);
			allIssues.addAll(regressions);
			
			this.unstable = unstable;
		}
		
		public List<OOReportEvent> getAllIssues() {
			return allIssues;
		}

		public List<OOReportEvent> getNewIssues() {
			return newIssues;
		}

		public List<OOReportRegressedEvent> getRegressions() {
			return regressions;
		}

		public boolean getUnstable() {
			return unstable;
		}
	}
	
	public static String getArcLink(ApiClient apiClient, String serviceId, String eventId, int timespan) {

		String result = EventUtil.getEventRecentLink(apiClient, serviceId, eventId, timespan);

		return result;
	}     

	public static RegressionReport execute(ApiClient apiClient, RegressionInput input, PrintStream output,
			boolean verbose) {

		RateRegression rateRegression = RegressionUtil.calculateRateRegressions(apiClient, input, output, verbose);
				
		List<OOReportEvent> newIssues = getAllNewEvents(apiClient, input.serviceId, input.baselineTimespan, rateRegression);
		List<OOReportRegressedEvent> regressions = getAllRegressions(apiClient, input.serviceId, input.baselineTimespan, rateRegression);

		boolean unstable = (rateRegression.getCriticalNewEvents().size() > 0)
				|| (rateRegression.getExceededNewEvents().size() > 0)
				|| (rateRegression.getCriticalRegressions().size() > 0);
		
		return new RegressionReport(newIssues, regressions, unstable);
	}

	private static List<OOReportEvent> getReportSevereEvents(ApiClient apiClient, String serviceId, int timespan,
			Collection<EventResult> events, String type) {

		List<OOReportEvent> result = new ArrayList<OOReportEvent>();

		for (EventResult event : events) {

			String arcLink = getArcLink(apiClient, serviceId, event.id, timespan);
			OOReportEvent reportEvent = new OOReportEvent(event, type, arcLink);

			result.add(reportEvent);
		}

		return result;
	}
	
	private static List<OOReportEvent> getReportNewEvents(ApiClient apiClient, String serviceId,
			int timespan, RateRegression rateRegression) {
		
		List<OOReportEvent> result = new ArrayList<OOReportEvent>();
		
		for (EventResult event : rateRegression.getAllNewEvents().values()) {
	
			if (rateRegression.getCriticalNewEvents().containsKey(event.id)) {
				continue;
			}
			
			if (rateRegression.getExceededNewEvents().containsKey(event.id)) {
				continue;
			}
			
			String arcLink = getArcLink(apiClient, serviceId, event.id, timespan);
	
			OOReportEvent newEvent = new OOReportEvent(event, RegressionStringUtil.NEW_ISSUE, arcLink);
	
			result.add(newEvent);
		}
		
		return result;
	}

	
	private static List<OOReportEvent> getAllNewEvents(ApiClient apiClient, String serviceId,
			int baselineTimespan, RateRegression rateRegression) {
		
		List<OOReportEvent> result = new ArrayList<OOReportEvent>();
		
		result.addAll(getReportSevereEvents(apiClient, serviceId, baselineTimespan,
			rateRegression.getCriticalNewEvents().values(), RegressionStringUtil.SEVERE_NEW));
		
		result.addAll(getReportSevereEvents(apiClient, serviceId, baselineTimespan,
			rateRegression.getExceededNewEvents().values(), RegressionStringUtil.SEVERE_NEW));
		
		result.addAll(getReportNewEvents(apiClient, serviceId, baselineTimespan, rateRegression));
		
		return result;
		
	}
	private static List<OOReportRegressedEvent> getAllRegressions(ApiClient apiClient, String serviceId,
			int timespan, RateRegression rateRegression) {

		List<OOReportRegressedEvent> result = new ArrayList<OOReportRegressedEvent>();

		for (RegressionResult regressionResult : rateRegression.getCriticalRegressions().values()) {

			String arcLink = getArcLink(apiClient, serviceId, regressionResult.getEvent().id, timespan);

			OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(regressionResult.getEvent(),
					regressionResult.getBaselineHits(), regressionResult.getBaselineInvocations(), RegressionStringUtil.SEVERE_REGRESSION, arcLink);

			result.add(regressedEvent);
		}
		
		for (RegressionResult regressionResult : rateRegression.getAllRegressions().values()) {

			if (rateRegression.getCriticalRegressions().containsKey(regressionResult.getEvent().id)) {
				continue;
			}
			
			String arcLink = getArcLink(apiClient, serviceId, regressionResult.getEvent().id, timespan);

			OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(regressionResult.getEvent(),
					regressionResult.getBaselineHits(), regressionResult.getBaselineInvocations(), RegressionStringUtil.REGRESSION, arcLink);

			result.add(regressedEvent);
		}

		return result;
	}
}
