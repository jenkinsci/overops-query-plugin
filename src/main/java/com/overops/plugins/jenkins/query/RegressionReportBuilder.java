package com.overops.plugins.jenkins.query;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.joda.time.DateTime;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.util.event.EventUtil;
import com.takipi.api.client.util.regression.RateRegression;
import com.takipi.api.client.util.regression.RegressionInput;
import com.takipi.api.client.util.regression.RegressionResult;
import com.takipi.api.client.util.regression.RegressionStringUtil;
import com.takipi.api.client.util.regression.RegressionUtil;

public class RegressionReportBuilder {
	
	private static class UniqueEventKey {
		
		private EventResult event;
		
		protected UniqueEventKey(EventResult event) {
			this.event = event;
		}
		
		@Override
		public int hashCode() {
			
			if (event.error_location == null) {
				return super.hashCode();
			}
			
			return event.error_location.hashCode();
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof UniqueEventKey)) {
				return false;
			}
			
			UniqueEventKey other = (UniqueEventKey)obj;

			
			if (!Objects.equal(event.type, other.event.type)) {
				return false;
			}
			
			if (!Objects.equal(event.error_origin, other.event.error_origin)) {
				return false;
			}
			
			if (!Objects.equal(event.error_location, other.event.error_location)) {
				return false;
			}
			
			if (!Objects.equal(event.name, other.event.name)) {
				return false;
			}
			
			if (!Objects.equal(event.call_stack_group, other.event.call_stack_group)) {
				return false;
			}
						
			return true;
		}
	}
	
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
		private final int uniqueEventsCount;
		private final int maxUniqueEvents;

		protected RegressionReport(RegressionInput input, RateRegression regression,
			List<OOReportEvent> newIssues, List<OOReportRegressedEvent> regressions, 
			List<OOReportEvent> topIssues, 
			long eventVolume, int maxEventVolume,
			int uniqueEventCounts, int maxUniqueEvents,
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
			this.uniqueEventsCount =  uniqueEventCounts;
			this.maxUniqueEvents = maxUniqueEvents;
			
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
		
		public int getMaxUniqueEvents() {
			return maxUniqueEvents; 
		}
		
		public long getUniqueEventsCount() {
			return uniqueEventsCount; 
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
	
	private static class ReportVolume {
		protected long volume;
		protected int eventCount;
		protected List<OOReportEvent> topEvents;
		protected boolean hasNewExceeded;
		protected boolean hasNewCritical;
		protected boolean hasCriticalRegression;
		protected Collection<EventResult> filter;
		
	}
	
	private static boolean allowEvent(EventResult event, Pattern pattern) {
		
		if (pattern == null ) {
			return true;
		}
		
		String json = new Gson().toJson(event);
		boolean result = pattern.matcher(json).find();
		
		return result;
	}
	
	private static List<EventResult> getSortedEventsByVolume(Collection<EventResult> events) {
		
		List<EventResult> result = new ArrayList<EventResult>(events);
		
		result.sort(new Comparator<EventResult>()
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
		
		return result;
	}
	
	private static void addEvent(Set<EventResult> events, EventResult event, 
		Pattern pattern, PrintStream output, boolean verbose) {
		
		if (allowEvent(event, pattern)) {
			events.add(event);
		} else if (output != null) {
			output.println(event + " did not match regexFilter and was skipped");
		}
	}
	
	private static Collection<EventResult> filterEvents(RateRegression rateRegression,
			Pattern pattern, PrintStream output, boolean verbose) {
		
		Set<EventResult> result = new HashSet<EventResult>();
		
		if (pattern != null) {
		
			for (EventResult event : rateRegression.getNonRegressions()) {
				addEvent(result, event, pattern, output, verbose);
			}
			
			for (EventResult event : rateRegression.getAllNewEvents().values()) {
				addEvent(result, event, pattern, output, verbose);
			}
			
			for (RegressionResult regressionResult : rateRegression.getAllRegressions().values()) {
				addEvent(result, regressionResult.getEvent(), pattern, output, verbose);

			}
			
		} else {
			result.addAll(rateRegression.getNonRegressions());
			result.addAll(rateRegression.getAllNewEvents().values());
		
			for (RegressionResult regressionResult : rateRegression.getAllRegressions().values()) {
				result.add(regressionResult.getEvent());
			}
		}
		
		return result;
	}
	
	private static ReportVolume getReportVolume(ApiClient apiClient, 
			RegressionInput input, RateRegression rateRegression,
			int limit, String regexFilter,  PrintStream output, boolean verbose) {
		
		ReportVolume result = new ReportVolume();
		
		Pattern pattern;
		
		if ((regexFilter != null) && (regexFilter.length() > 0)) {
			pattern = Pattern.compile(regexFilter);
		} else {
			pattern = null;
		}
		
		Collection<EventResult> eventsSet = filterEvents(rateRegression, pattern, output, verbose);		
		List<EventResult> events =  getSortedEventsByVolume(eventsSet);
		
		if (pattern != null) {
			result.filter = eventsSet;
		}
				
		result.topEvents = new ArrayList<OOReportEvent>();
		
		for (int i = 0; i < Math.min(limit, events.size()); i++) {
			EventResult event = events.get(i);
			String arcLink = getArcLink(apiClient, event.id, input, rateRegression);
			result.topEvents.add(new OOReportEvent(event, arcLink));
		}
				
		for (EventResult event : events) {
			
			if (event.stats != null) {
				result.volume += event.stats.hits;
			}
			
			if (rateRegression.getCriticalRegressions().containsKey(event.id)) {
				result.hasCriticalRegression = true;
			}
			
			if (rateRegression.getCriticalNewEvents().containsKey(event.id)) {
				result.hasNewCritical = true;
			}
			
			if (rateRegression.getSortedExceededNewEvents().contains(event)) {
				result.hasNewExceeded = true;
			}
		}
		
		result.eventCount = getUniqueErrorCount(events); 
		
		return result;
	}
		
	private static int getUniqueErrorCount(Collection<EventResult> events) {
		
		Set<UniqueEventKey> grouped = new HashSet<UniqueEventKey>(events.size());
		
		for (EventResult event : events) {
			UniqueEventKey uniqueEventKey = new UniqueEventKey(event);
			grouped.add(uniqueEventKey);
		}
		
		int result = grouped.size();
		
		return result;		
	}
	
	public static RegressionReport execute(ApiClient apiClient, RegressionInput input, 
			int maxEventVolume, int maxUniqueErrors, int topEventLimit, 
			String regexFilter, PrintStream output, boolean verbose) {

		RateRegression rateRegression = RegressionUtil.calculateRateRegressions(apiClient, input, output, verbose);	

		ReportVolume reportVolume = getReportVolume(apiClient, input, 
				rateRegression, topEventLimit, regexFilter, output, verbose);
			
		List<OOReportEvent> newIssues = getAllNewEvents(apiClient, input, rateRegression, reportVolume.filter);
		List<OOReportRegressedEvent> regressions = getAllRegressions(apiClient, input, rateRegression, reportVolume.filter);
		
		boolean maxUniqueErrorsExceeded;
		boolean maxVolumeExceeded = (maxEventVolume > 0) && (reportVolume.volume > maxEventVolume);
		
		int uniqueEventCount;
		
		if (maxUniqueErrors > 0) {
			uniqueEventCount = reportVolume.eventCount;
			maxUniqueErrorsExceeded = reportVolume.eventCount > maxUniqueErrors;
		} else {
			uniqueEventCount = 0;
			maxUniqueErrorsExceeded = false;
		}
			
		boolean unstable = (reportVolume.hasNewCritical)
				|| (reportVolume.hasNewExceeded)
				|| (reportVolume.hasCriticalRegression)
				|| (maxVolumeExceeded)
				|| (maxUniqueErrorsExceeded);
		
		return new RegressionReport(input, rateRegression, newIssues, regressions,
				reportVolume.topEvents, reportVolume.volume, maxEventVolume, 
				uniqueEventCount, maxUniqueErrors, unstable);
	}

	private static List<OOReportEvent> getReportSevereEvents(ApiClient apiClient, 
		RegressionInput input, RateRegression regression, 
		Collection<EventResult> events, Collection<EventResult> filter, String type) {

		List<OOReportEvent> result = new ArrayList<OOReportEvent>();

		for (EventResult event : events) {

			if ((filter != null) && (!filter.contains(event))) {
				continue;
			}
			
			String arcLink = getArcLink(apiClient, event.id, input, regression);
			OOReportEvent reportEvent = new OOReportEvent(event, type, arcLink);

			result.add(reportEvent);
		}

		return result;
	}
	
	private static List<OOReportEvent> getReportNewEvents(ApiClient apiClient, 
			RegressionInput input, RateRegression rateRegression,
			Collection<EventResult> filter) {
		
		List<OOReportEvent> result = new ArrayList<OOReportEvent>();
		
		for (EventResult event : rateRegression.getAllNewEvents().values()) {
	
			if ((filter != null) && (!filter.contains(event))) {
				continue;
			}
			
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
			RegressionInput input, RateRegression rateRegression, Collection<EventResult> filter) {
		
		List<OOReportEvent> result = new ArrayList<OOReportEvent>();
		
		result.addAll(getReportSevereEvents(apiClient, input, rateRegression,
			rateRegression.getCriticalNewEvents().values(), filter, RegressionStringUtil.SEVERE_NEW));
		
		result.addAll(getReportSevereEvents(apiClient, input, rateRegression,
			rateRegression.getExceededNewEvents().values(), filter, RegressionStringUtil.SEVERE_NEW));
		
		result.addAll(getReportNewEvents(apiClient, input, rateRegression, filter));
		
		return result;
		
	}
	
	private static List<OOReportRegressedEvent> getAllRegressions(ApiClient apiClient, 
			RegressionInput input, RateRegression rateRegression, Collection<EventResult> filter) {

		List<OOReportRegressedEvent> result = new ArrayList<OOReportRegressedEvent>();

		for (RegressionResult regressionResult : rateRegression.getCriticalRegressions().values()) {

			if ((filter != null) && (!filter.contains(regressionResult.getEvent()))) {
				continue;
			}
			
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
