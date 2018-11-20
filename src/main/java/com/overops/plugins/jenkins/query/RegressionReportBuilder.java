package com.overops.plugins.jenkins.query;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.joda.time.DateTime;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.util.event.EventUtil;
import com.takipi.api.client.util.regression.RateRegression;
import com.takipi.api.client.util.regression.RegressionInput;
import com.takipi.api.client.util.regression.RegressionResult;
import com.takipi.api.client.util.regression.RegressionStringUtil;
import com.takipi.api.client.util.regression.RegressionUtil;
import com.takipi.common.util.Pair;

public class RegressionReportBuilder {
	
	public static class RegressionReport {

		private final List<OOReportEvent> newIssues;
		private final List<OOReportRegressedEvent> regressions;
		private final List<OOReportEvent> allIssues;
		private final List<OOReportEvent> topIssues;
		private final boolean unstable;
		private final RegressionInput input;
		private final RateRegression regression;
		private final long eventVolume;
		private final int maxEventVolume;

		protected RegressionReport(RegressionInput input, RateRegression regression,
			List<OOReportEvent> newIssues, List<OOReportRegressedEvent> regressions, 
			List<OOReportEvent> topIssues, 
			long eventVolume, int maxEventVolume,
			boolean unstable) {
			
			this.input = input;
			this.regression = regression;
			
			this.newIssues = newIssues;
			this.regressions = regressions;
			this.topIssues = topIssues;
			this.allIssues = new ArrayList<OOReportEvent>();
			
			allIssues.addAll(newIssues);
			allIssues.addAll(regressions);
						
			this.eventVolume =  eventVolume;
			this.maxEventVolume = maxEventVolume;
			
			this.unstable = unstable;
		}
		
		public RegressionInput getInput() {
			return input;
		}

		public RateRegression getRegression() {
			return regression;
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
		
		public List<OOReportEvent> getTopIssues() {
			return topIssues;
		}
		
		public int getMaxEventVolume() {
			return maxEventVolume; 
		}
		
		public long getEventVolume() {
			return eventVolume; 
		}
		
		public boolean getUnstable() {
			return unstable;
		}
	}
	
	public static String getArcLink(ApiClient apiClient, String eventId,
			RegressionInput input, RateRegression regression) {

		DateTime activeWndowStart = regression.getActiveWndowStart();
		DateTime from = activeWndowStart.minusMinutes(input.baselineTimespan); 
		
		String result = EventUtil.getEventRecentLinkDefault(apiClient, input.serviceId, eventId, 
				from, DateTime.now(), input.applictations, input.servers, input.deployments, 
				EventUtil.DEFAULT_PERIOD);

		return result;
	}
	
	
	private static Pair<List<OOReportEvent>, Long> getHighVolumeEvents(ApiClient apiClient, 
			RegressionInput input, RateRegression rateRegression,
			List<OOReportEvent> newIssues,List<OOReportRegressedEvent> regressions, 
			Collection<EventResult> nonRegressions, int limit) {
		
		List<EventResult> events = new ArrayList<EventResult>(newIssues.size() 
			+ regressions.size() + nonRegressions.size());
		
		for (OOReportEvent reportEvent : newIssues) {
			events.add(reportEvent.getEvent());
		}
		
		for (OOReportRegressedEvent regression : regressions) {
			events.add(regression.getEvent());
		}
		
		events.addAll(nonRegressions);
		
		events.sort(new Comparator<EventResult>()
		{

			@Override
			public int compare(EventResult o1, EventResult o2)
			{
				long v1;
				long v2;
				
				if (o1.stats != null) {
					v1 = o1.stats.hits;
				} else {
					v1 = 0;
				}
				
				if (o2.stats != null) {
					v2 = o2.stats.hits;
				} else {
					v2 = 0;
				}
				
				return (int)(v2 - v1);
			}
		});
		
		List<OOReportEvent> result = new ArrayList<OOReportEvent>();
		
		for (int i = 0; i < Math.min(limit, events.size()); i++) {
			EventResult event = events.get(i);
			String arcLink = getArcLink(apiClient, event.id, input, rateRegression);
			result.add(new OOReportEvent(event, "moo", arcLink));
		}
		
		long volume = 0;
		
		for (EventResult event : events) {
			if (event.stats != null) {
				volume += event.stats.hits;
			}
		}
		
		return Pair.of(result, Long.valueOf(volume));
	}
		
	public static RegressionReport execute(ApiClient apiClient, RegressionInput input, 
			int maxEventVolume, int topEventLimit, PrintStream output, boolean verbose) {

		RateRegression rateRegression = RegressionUtil.calculateRateRegressions(apiClient, input, output, verbose);	

		List<OOReportEvent> newIssues = getAllNewEvents(apiClient, input, rateRegression);
		List<OOReportRegressedEvent> regressions = getAllRegressions(apiClient, input, rateRegression);

		Pair<List<OOReportEvent>, Long> highVolumeEvents = getHighVolumeEvents(apiClient, input, 
				rateRegression, newIssues, regressions, rateRegression.getNonRegressions(), topEventLimit);
		
		boolean maxVolumeExeceeded = (maxEventVolume > 0) && (highVolumeEvents.getSecond().intValue() > maxEventVolume);
		
		boolean unstable = (rateRegression.getCriticalNewEvents().size() > 0)
				|| (rateRegression.getExceededNewEvents().size() > 0)
				|| (rateRegression.getCriticalRegressions().size() > 0)
				|| (maxVolumeExeceeded);
		
		return new RegressionReport(input, rateRegression, newIssues, 
			regressions, highVolumeEvents.getFirst(), 
			highVolumeEvents.getSecond().longValue(), maxEventVolume, unstable);
	}

	private static List<OOReportEvent> getReportSevereEvents(ApiClient apiClient, 
		RegressionInput input, RateRegression regression, 
		Collection<EventResult> events, String type) {

		List<OOReportEvent> result = new ArrayList<OOReportEvent>();

		for (EventResult event : events) {

			String arcLink = getArcLink(apiClient, event.id, input, regression);
			OOReportEvent reportEvent = new OOReportEvent(event, type, arcLink);

			result.add(reportEvent);
		}

		return result;
	}
	
	private static List<OOReportEvent> getReportNewEvents(ApiClient apiClient, 
			RegressionInput input, RateRegression rateRegression) {
		
		List<OOReportEvent> result = new ArrayList<OOReportEvent>();
		
		for (EventResult event : rateRegression.getAllNewEvents().values()) {
	
			if (rateRegression.getCriticalNewEvents().containsKey(event.id)) {
				continue;
			}
			
			if (rateRegression.getExceededNewEvents().containsKey(event.id)) {
				continue;
			}
			
			String arcLink = getArcLink(apiClient, event.id, input, rateRegression);
	
			OOReportEvent newEvent = new OOReportEvent(event, RegressionStringUtil.NEW_ISSUE, arcLink);
	
			result.add(newEvent);
		}
		
		return result;
	}

	
	private static List<OOReportEvent> getAllNewEvents(ApiClient apiClient,
			RegressionInput input, RateRegression rateRegression) {
		
		List<OOReportEvent> result = new ArrayList<OOReportEvent>();
		
		result.addAll(getReportSevereEvents(apiClient, input, rateRegression,
			rateRegression.getCriticalNewEvents().values(), RegressionStringUtil.SEVERE_NEW));
		
		result.addAll(getReportSevereEvents(apiClient, input, rateRegression,
			rateRegression.getExceededNewEvents().values(), RegressionStringUtil.SEVERE_NEW));
		
		result.addAll(getReportNewEvents(apiClient, input, rateRegression));
		
		return result;
		
	}
	
	private static List<OOReportRegressedEvent> getAllRegressions(ApiClient apiClient, 
			RegressionInput input, RateRegression rateRegression) {

		List<OOReportRegressedEvent> result = new ArrayList<OOReportRegressedEvent>();

		for (RegressionResult regressionResult : rateRegression.getCriticalRegressions().values()) {

			String arcLink = getArcLink(apiClient, regressionResult.getEvent().id, input, rateRegression);

			OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(regressionResult.getEvent(),
					regressionResult.getBaselineHits(), regressionResult.getBaselineInvocations(), RegressionStringUtil.SEVERE_REGRESSION, arcLink);

			result.add(regressedEvent);
		}
		
		for (RegressionResult regressionResult : rateRegression.getAllRegressions().values()) {

			if (rateRegression.getCriticalRegressions().containsKey(regressionResult.getEvent().id)) {
				continue;
			}
			
			String arcLink = getArcLink(apiClient, regressionResult.getEvent().id, input, rateRegression);

			OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(regressionResult.getEvent(),
					regressionResult.getBaselineHits(), regressionResult.getBaselineInvocations(), RegressionStringUtil.REGRESSION, arcLink);

			result.add(regressedEvent);
		}

		return result;
	}
}
