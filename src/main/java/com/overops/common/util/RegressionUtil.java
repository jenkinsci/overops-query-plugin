package com.overops.common.util;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.collect.Maps;
import com.overops.common.api.util.ApiViewUtil;
import com.overops.common.util.RegressionUtil.RegressionStats.SeasonlityResult;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.data.event.Stats;
import com.takipi.common.api.data.metrics.Graph;
import com.takipi.common.api.data.metrics.Graph.GraphPoint;
import com.takipi.common.api.data.metrics.Graph.GraphPointContributor;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.volume.EventsVolumeResult;
import com.takipi.common.api.util.CollectionUtil;
import com.takipi.common.api.util.Pair;

public class RegressionUtil {

	public static class RegressionResult {

		private EventResult event;
		private long baselineHits;
		private long baselinInvocations;

		public EventResult getEvent() {
			return event;
		}

		public long getBaselineHits() {
			return baselineHits;
		}

		public long getBaselineInvocations() {
			return baselinInvocations;
		}

		public static RegressionResult of(EventResult event, long baselineHits, long baselinInvocations) {
			RegressionResult result = new RegressionResult();
			result.event = event;
			result.baselineHits = baselineHits;
			result.baselinInvocations = baselinInvocations;

			return result;
		}
	}

	public static class RegressionStats {
		public final long hits;
		public final long invocations;
		public final double rate;

		public final double hitsAvg;
		public final double invocationsAvg;
		public final double rateAvg;

		public final double hitsAvgStdDev;
		public final double invocationsAvgStdDev;
		public final double rateAvgStdDev;

		private RegressionStats(long hits, long invocations, double rate, double hitsAvg, double invocationsAvg,
				double rateAvg, double hitsAvgStdDev, double invocationsAvgStdDev, double rateAvgStdDev) {
			this.hits = hits;
			this.invocations = invocations;
			this.rate = rate;

			this.hitsAvg = hitsAvg;
			this.invocationsAvg = invocationsAvg;
			this.rateAvg = rateAvg;

			this.hitsAvgStdDev = hitsAvgStdDev;
			this.invocationsAvgStdDev = invocationsAvgStdDev;
			this.rateAvgStdDev = rateAvgStdDev;
		}

		public static class SeasonlityResult {
			public final long largerVolumePeriod;
			public final long halfVolumePeriods;
			public final int largerVolumePriodIndex;

			private SeasonlityResult(long largerVolumePeriod, long halfVolumePeriods, int largerVolumePriodIndex) {
				this.largerVolumePeriod = largerVolumePeriod;
				this.halfVolumePeriods = halfVolumePeriods;
				this.largerVolumePriodIndex = largerVolumePriodIndex;
			}
		}

		static RegressionStats of(long hits, long invocations, double rate, double hitsAvg, double invocationsAvg,
				double rateAvg, double hitsAvgStdDev, double invocationsAvgStdDev, double rateAvgStdDev) {
			return new RegressionStats(hits, invocations, rate, hitsAvg, invocationsAvg, rateAvg, hitsAvgStdDev,
					invocationsAvgStdDev, rateAvgStdDev);
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();

			result.append("hits = ");
			result.append(hits);

			result.append(", invocations = ");
			result.append(invocations);

			result.append(", rate = ");
			result.append(rate);

			result.append(", hitsAvg = ");
			result.append(hitsAvg);

			result.append(", invocationsAvg = ");
			result.append(invocationsAvg);

			result.append(", rateAvg = ");
			result.append(rateAvg);

			result.append(", hitsAvgStdDev = ");
			result.append(hitsAvgStdDev);

			result.append(", invocationsAvgStdDev = ");
			result.append(invocationsAvgStdDev);

			result.append(", rateAvgStdDev = ");
			result.append(rateAvgStdDev);

			return result.toString();
		}
	}

	public static class RateRegressionBuilder {
		private final Map<String, EventResult> allNewEvents;

		private final Map<String, RegressionResult> allRegressions;
		private final Map<String, RegressionResult> criticalRegressions;

		private final Map<String, EventResult> exceededNewEvents;
		private final Map<String, EventResult> criticalNewEvents;

		RateRegressionBuilder() {
			allRegressions = Maps.newHashMap();
			criticalNewEvents = Maps.newHashMap();
			exceededNewEvents = Maps.newHashMap();
			allNewEvents = Maps.newHashMap();
			criticalRegressions = Maps.newHashMap();
		}

		public void addNewEvent(String id, EventResult event) {
			allNewEvents.put(id, event);
		}

		public void addExceddedNewEvent(String id, EventResult event) {
			exceededNewEvents.put(id, event);
		}

		public void addCriticalNewEvent(String id, EventResult event) {
			criticalNewEvents.put(id, event);
		}

		public void addRegression(String id, EventResult event, long baselineHits, long baselineInvocations) {
			allRegressions.put(id, RegressionResult.of(event, baselineHits, baselineInvocations));
		}

		public void addCriticalRegression(String id, EventResult event, long baselineHits, long baselineInvocations) {
			criticalRegressions.put(id, RegressionResult.of(event, baselineHits, baselineInvocations));
		}

		public RateRegression build() {
			return new RateRegression(allNewEvents, allRegressions, criticalRegressions, exceededNewEvents,
					criticalNewEvents);
		}
	}

	public static class RateRegression {
		private final Map<String, EventResult> allNewEvents;

		private final Map<String, RegressionResult> allRegressions;
		private final Map<String, RegressionResult> criticalRegressions;

		private final Map<String, EventResult> exceededNewEvents;
		private final Map<String, EventResult> criticalNewEvents;

		RateRegression(Map<String, EventResult> allNewEvents, Map<String, RegressionResult> allRegressions,
				Map<String, RegressionResult> criticalRegressions, Map<String, EventResult> exceededNewEvents,
				Map<String, EventResult> criticalNewEvents) {

			this.allRegressions = Collections.unmodifiableMap(allRegressions);
			this.criticalNewEvents = Collections.unmodifiableMap(criticalNewEvents);
			this.exceededNewEvents = Collections.unmodifiableMap(exceededNewEvents);
			this.allNewEvents = Collections.unmodifiableMap(allNewEvents);
			this.criticalRegressions = Collections.unmodifiableMap(criticalRegressions);
		}

		public Map<String, EventResult> getAllNewEvents() {
			return allNewEvents;
		}

		public Map<String, RegressionResult> getAllRegressions() {
			return allRegressions;
		}

		public Map<String, RegressionResult> getCriticalRegressions() {
			return criticalRegressions;
		}

		public Map<String, EventResult> getExceededNewEvents() {
			return exceededNewEvents;
		}

		public Map<String, EventResult> getCriticalNewEvents() {
			return criticalNewEvents;
		}
	}

	private static boolean hasOlderRelatedEvents(EventResult event, PrintStream printStream, boolean verbose) {
		if (event.similar_event_ids == null) {
			return false;
		}

		if (event.similar_event_ids.size() == 0) {
			return false;
		}

		int eventId;

		try {
			eventId = Integer.parseInt(event.id);
		} catch (Exception e) {
			return false;
		}

		for (String similarId : event.similar_event_ids) {
			int similarEventId;

			try {
				similarEventId =  Integer.parseInt(similarId);
			} catch (Exception e) {
				continue;
			}

			if (eventId > similarEventId) {
				if ((verbose) && (printStream != null)) {
					printStream
							.println("New event " + event.toString() + " has older similar event with ID " + similarId);
				}

				return true;
			}
		}

		return false;
	}

	private static String printEvent(EventResult event) {
		StringBuilder result = new StringBuilder();

		result.append("Event ");
		result.append(event.id);
		result.append(" ");
		result.append(event.summary);

		if (!event.summary.contains("in")) {
			result.append(" in ");
			String[] parts = event.error_location.class_name.split(Pattern.quote("."));

			if (parts.length <= 1) {
				result.append(event.error_location.class_name);
			} else {
				result.append(parts[parts.length - 1]);
			}

			result.append(".");
			result.append(event.error_location.method_name);
		}

		if ((event.similar_event_ids != null) && (event.similar_event_ids.size() > 0)) {
			result.append("(");
			result.append(String.join(",", event.similar_event_ids));
			result.append(")");
		}

		return result.toString();
	}

	private static final DecimalFormat df = new DecimalFormat("#.000");

	private static String f(double d) {
		if (d % 1 == 0) {
			return String.valueOf((int) d);
		}

		String result = df.format(d);

		if (result.startsWith(".")) {
			return "0" + result;
		}

		return result;
	}

	private static Map<String, long[]> getPeriodVolumes(DateTime startTime, Graph baselineGraph, int activeTimespan,
			int baselineTimespan) {
		Map<String, long[]> result = Maps.newHashMap();

		DateTime baselineStart = startTime.minusMinutes(baselineTimespan + activeTimespan);
		int timeWindows = baselineTimespan / activeTimespan;

		for (GraphPoint gp : baselineGraph.points) {
			DateTime firstSeen = ISODateTimeFormat.dateTimeParser().parseDateTime(gp.time);
			Minutes timeDelta = Minutes.minutesBetween(baselineStart, firstSeen);

			for (GraphPointContributor gpc : gp.contributors) {
				long[] timeWindowVolumes = result.get(gpc.id);

				if (timeWindowVolumes == null) {
					timeWindowVolumes = new long[timeWindows];
					result.put(gpc.id, timeWindowVolumes);
				}

				int minutes = timeDelta.getMinutes();
				int index = Math.min(minutes / activeTimespan, timeWindowVolumes.length - 1);

				timeWindowVolumes[index] += gpc.stats.hits;
			}
		}

		return result;
	}

	enum REGRESSION {
		YES, NO, NO_DATA;
	}

	private static String ps(Stats stats) {
		if (stats == null) {
			return "(null)";
		}
		return "(" + stats.hits + "/" + stats.invocations + ")";

	}

	private static REGRESSION processNewsIssue(EventResult activeEvent, DateTime activeFrom, int minVolumeThreshold,
			double minErrorRateThreshold, Collection<String> criticalExceptionTypes, 
			RateRegressionBuilder rateRegression, PrintStream printStream, boolean verbose) {
		DateTime firstSeen = ISODateTimeFormat.dateTimeParser().parseDateTime(activeEvent.first_seen);

		boolean isEventNew = firstSeen.isAfter(activeFrom);
		boolean hasOlderSmiliarEvent = hasOlderRelatedEvents(activeEvent, printStream, verbose);

		boolean isNew = (isEventNew) && (!hasOlderSmiliarEvent);

		if (isNew) {
			rateRegression.addNewEvent(activeEvent.id, activeEvent);

			// events types in the critical list are considered as new regardless of
			// threshold

			boolean isUncaught = activeEvent.type.equals("Uncaught Exception");
			boolean isCriticalEventType = criticalExceptionTypes.contains(activeEvent.name);

			if ((isUncaught) || (isCriticalEventType)) {
				rateRegression.addCriticalNewEvent(activeEvent.id, activeEvent);

				if (printStream != null) {
					printStream
							.println(printEvent(activeEvent) + " is critical new event with " + activeEvent.stats.hits + "hits. \n");
				}

				return REGRESSION.YES;
			}
		}

		if ((activeEvent.stats == null) || (activeEvent.stats.invocations == 0) || (activeEvent.stats.hits == 0)) {
			if ((verbose) && (printStream != null)) {
				printStream.println("No stats " + ps(activeEvent.stats) + printEvent(activeEvent));
			}
			return REGRESSION.NO_DATA;
		}

		double activeEventRatio = ((double) activeEvent.stats.hits / (double) activeEvent.stats.invocations);

		if ((activeEventRatio < minErrorRateThreshold) || (activeEvent.stats.hits < minVolumeThreshold)) {
			if ((verbose) && (printStream != null)) {

				printStream.println("Min thrs " + ps(activeEvent.stats) + printEvent(activeEvent)
						+ "fails " + "hits" + activeEvent.stats.hits + "ratio: " + activeEventRatio);
			}

			return REGRESSION.NO_DATA;
		}

		if (isNew) {
			rateRegression.addExceddedNewEvent(activeEvent.id, activeEvent);

			if (printStream != null) {
				printStream.println(printEvent(activeEvent) + " is new with rate: " + activeEventRatio + " hits: "
						+ activeEvent.stats.hits + "\n");
			}

			return REGRESSION.YES;
		}

		return REGRESSION.NO;
	}

	private static SeasonlityResult calculateSeasonality(EventResult activeEvent,
			Map<String, long[]> periodVolumes) {

		long[] eventPeriodVolumes = periodVolumes.get(activeEvent.id);

		long largerVolumePeriod = -1;
		int largerVolumePriodIndex = -1;
		long halfVolumePeriods = 0;

		if (eventPeriodVolumes != null) {
			for (int index = 0; index < eventPeriodVolumes.length; index++) {

				long periodVolume = eventPeriodVolumes[index];

				if (periodVolume > activeEvent.stats.hits) {
					largerVolumePriodIndex = index;
					largerVolumePeriod = periodVolume;
					break;
				}

				if (periodVolume > activeEvent.stats.hits / 2) {
					halfVolumePeriods++;
				}
			}
		}

		return new SeasonlityResult(largerVolumePeriod, halfVolumePeriods, largerVolumePriodIndex);
	}

	private static REGRESSION processVolumeRegression(EventResult activeEvent, int activeTimespan, int baselineTimespan,
			Map<String, RegressionStats> regressionsStats, Map<String, long[]> periodVolumes, double reggressionDelta,
			double criticalRegressionDelta, boolean applySeasonality, RateRegressionBuilder rateRegression,
			PrintStream printStream, boolean verbose) {
		
		if (reggressionDelta == 0) {
			return REGRESSION.NO;
		}

		RegressionStats regressionStats = regressionsStats.get(activeEvent.id);

		if (regressionStats == null) {
			if  ((verbose) && (printStream != null)) {
				printStream.println("No regression stats " + printEvent(activeEvent));
			}
			return REGRESSION.NO_DATA;
		}

		double normalizedActiveVolume = (double) (activeEvent.stats.hits) / (double) (activeTimespan);
		double normalizedActiveInv = (double) (activeEvent.stats.invocations) / (double) (activeTimespan);

		double normalizedBaselineVolume = (double) (regressionStats.hits) / (double) (baselineTimespan);
		double normalizedBaselineInv = (double) (regressionStats.invocations) / (double) (baselineTimespan);

		double volRateDelta = ((double) (normalizedActiveVolume) / (double) (normalizedBaselineVolume)) - 1;
		double invRateDelta = ((double) (normalizedActiveInv) / (double) (normalizedBaselineInv)) - 1;

		boolean isRegression = volRateDelta - Math.max(invRateDelta * 2, 0) > reggressionDelta;
		boolean isCriticalRegression = volRateDelta - Math.max(invRateDelta * 2, 0) > criticalRegressionDelta;
		
		if (!isRegression) {
			if  ((verbose) && (printStream != null)) {
				printStream.println("Not regressed " + printEvent(activeEvent));
			}
			return REGRESSION.NO;
		}

		SeasonlityResult seasonlityResult;
		
		if (applySeasonality ) {
			seasonlityResult = calculateSeasonality(activeEvent, periodVolumes);
		} else {
			seasonlityResult = null;
		}

		if (printStream != null) {
			printStream.println(printEvent(activeEvent) + " regression\nRelHit: " + f(normalizedBaselineVolume) + " -> "
					+ f(normalizedActiveVolume) + "\nRelInv: " + f(normalizedBaselineInv) + " -> "
					+ f(normalizedActiveInv) + "\nVolDel: " + f(volRateDelta) + ", InvDel " + f(invRateDelta)
					+ "\nhits: " + f(regressionStats.hits) + " -> " + f(activeEvent.stats.hits) + "\ninv: "
					+ f(regressionStats.invocations) + " -> " + f(activeEvent.stats.invocations));
		}

		if ((applySeasonality) && (seasonlityResult.largerVolumePeriod >= 0)) {
			if (printStream != null) {
				printStream.println("Period " + seasonlityResult.largerVolumePriodIndex + " = " + seasonlityResult.largerVolumePeriod
						+ " > active volume. Aborting regression\n");
			}

			return REGRESSION.NO;
		}

		if  ((applySeasonality) && (seasonlityResult.halfVolumePeriods >= 2)) {
			if (printStream != null) {
				printStream.println(seasonlityResult.halfVolumePeriods + " periods > 50% active volume detected. Aborting regression\n");
			}

			return REGRESSION.NO;
		}

		rateRegression.addRegression(activeEvent.id, activeEvent, regressionStats.hits, regressionStats.invocations);

		if ((isCriticalRegression) && (criticalRegressionDelta > 0)) {
			rateRegression.addCriticalRegression(activeEvent.id, activeEvent, regressionStats.hits, regressionStats.invocations);
		}

		if (printStream != null) {
			printStream.println("\n");
		}
		
		return REGRESSION.YES;
	}

	public static RateRegression calculateRateRegressions(ApiClient apiClient, String serviceId, String viewId,
			int activeTimespan, int baselineTimespan, int minVolumeThreshold, double minErrorRateThreshold,
			double reggressionDelta, double criticalRegressionDelta, boolean applySeasonality, Collection<String> criticalExceptionTypes,
			PrintStream printStream, boolean verbose) {

		RateRegressionBuilder result = new RateRegressionBuilder();

		DateTime fromTime = DateTime.now().minusWeeks(1);
		DateTime activeFrom = fromTime.minusMinutes(activeTimespan);
		DateTime baselineFrom = fromTime.minusMinutes(baselineTimespan);

		if (printStream != null) {
			printStream.print("Begin regression analysis\n");
		}
		
		EventsVolumeResult activeEventVolume = ApiViewUtil.getEventsVolume(apiClient, serviceId, viewId, activeFrom,
				fromTime);

		
		if ((activeEventVolume == null) || (activeEventVolume.events == null)) {
			
			if (printStream != null) {
				printStream.println("SEVERE: Could not get event volume for " + serviceId + " " + viewId);
			}

			return result.build();
		}

		Graph baselineGraph = ApiViewUtil.getEventsGraph(apiClient, serviceId, viewId,
				baselineTimespan / activeTimespan * 2, baselineFrom, activeFrom);

		Map<String, RegressionStats> regressionsStats;
		Map<String, long[]> periodVolumes;

		if (baselineGraph != null) {
			regressionsStats = processEventsGraph(baselineGraph);
			periodVolumes = getPeriodVolumes(fromTime, baselineGraph, activeTimespan, baselineTimespan);
		} else {
			regressionsStats = null;
			periodVolumes = null;
		}

		List<EventResult> nonRegressions = new ArrayList<EventResult>();

		for (EventResult activeEvent : activeEventVolume.events) {

			REGRESSION isNewIssue = processNewsIssue(activeEvent, activeFrom, minVolumeThreshold, minErrorRateThreshold,
					criticalExceptionTypes, result, printStream, verbose);

			if (isNewIssue.equals(REGRESSION.YES)) {
				continue;
			}

			if (isNewIssue.equals(REGRESSION.NO_DATA)) {
				nonRegressions.add(activeEvent);
				continue;
			}

			if (baselineGraph != null) {
				REGRESSION regResult = processVolumeRegression(activeEvent, activeTimespan, baselineTimespan,
						regressionsStats, periodVolumes, reggressionDelta, criticalRegressionDelta, applySeasonality,
						result, printStream, verbose);

				if (!regResult.equals(REGRESSION.YES)) {
					nonRegressions.add(activeEvent);
				}
			}
		}

		if (verbose) {
			printNonRegressions(nonRegressions, printStream);
		}
		

		return result.build();
	}

	private static void printNonRegressions(List<EventResult> nonRegressions, PrintStream printStream) {
		if (printStream != null) {

			nonRegressions.sort(new Comparator<EventResult>() {

				@Override
				public int compare(EventResult o1, EventResult o2) {
					return (int) o1.stats.hits - (int) o2.stats.hits;
				}
			});

			printStream.println("\nNon regressions:\n");

			for (EventResult event : nonRegressions) {
				printStream.println(event.stats.hits + " " + printEvent(event));
			}
		}
	}

	private static Map<String, RegressionStats> processEventsGraph(Graph graph) {
		// Maps from event id to a list of hits/invocations.
		// Uses long[] to optimize data size, we know how many points are there based on
		// the graph.
		Map<String, Pair<long[], long[]>> rawData = Maps.newHashMap();

		int pointsCount = graph.points.size();

		for (int i = 0; i < pointsCount; i++) {
			GraphPoint currentPoint = graph.points.get(i);

			if (CollectionUtil.safeIsEmpty(currentPoint.contributors)) {
				continue;
			}

			for (GraphPointContributor contributor : currentPoint.contributors) {
				if (contributor.stats == null) {
					continue;
				}

				Pair<long[], long[]> eventData = rawData.get(contributor.id);

				if (eventData == null) {
					eventData = Pair.of(new long[pointsCount], new long[pointsCount]);
					rawData.put(contributor.id, eventData);
				}

				eventData.getFirst()[i] = contributor.stats.hits;
				eventData.getSecond()[i] = contributor.stats.invocations;
			}
		}

		Map<String, RegressionStats> result = Maps.newHashMapWithExpectedSize(rawData.size());

		for (Map.Entry<String, Pair<long[], long[]>> entry : rawData.entrySet()) {
			result.put(entry.getKey(), buildRegressionStats(entry.getValue()));
		}

		return result;
	}

	private static RegressionStats buildRegressionStats(Pair<long[], long[]> eventStats) {
		long[] hitsArr = eventStats.getFirst();
		long[] invocationsArr = eventStats.getSecond();

		double[] rateArr = getRateArr(hitsArr, invocationsArr);

		long hitsSum = MathUtil.sum(hitsArr);
		long invocationsSum = MathUtil.sum(invocationsArr);
		double overallRate = (invocationsSum == 0 ? 0.0 : ((double) hitsSum / (double) invocationsSum));

		double hitsAvg = MathUtil.avg(hitsArr);
		double invocationsAvg = MathUtil.avg(invocationsArr);
		double rateAvg = MathUtil.avg(rateArr);

		double hitsAvgStdDev = MathUtil.stdDev(hitsArr);
		double invocationsAvgStdDev = MathUtil.stdDev(invocationsArr);
		double rateAvgStdDev = MathUtil.stdDev(rateArr);

		return RegressionStats.of(hitsSum, invocationsSum, overallRate, hitsAvg, invocationsAvg, rateAvg, hitsAvgStdDev,
				invocationsAvgStdDev, rateAvgStdDev);
	}

	public static double[] getRateArr(long[] hits, long[] invocations) {
		double[] result = new double[hits.length];

		for (int i = 0; i < hits.length; i++) {
			long curHits = hits[i];
			long curInvocations = invocations[i];

			result[i] = (curInvocations == 0 ? 0.0 : ((double) curHits / (double) curInvocations));
		}

		return result;
	}
}
