package com.overops.plugins.jenkins.query;

import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.util.regression.RegressionStringUtil;

public class OOReportEvent {
	
	protected final EventResult event;
	protected final String arcLink;
	protected final String type;

	public OOReportEvent(EventResult event, String arcLink) {
		this(event, null, arcLink);
	}
	
	public OOReportEvent(EventResult event, String type, String arcLink) {
		this.event = event;
		this.arcLink = arcLink;
		this.type = type;
	}
	
	public EventResult getEvent() {
		return event;
	}

	public String getEventSummary() {
		String result = RegressionStringUtil.getEventSummary(event);
		return result;
	}

	public String getEventRate() {
		String result =  RegressionStringUtil.getEventRate(event);
		return result;
	}

	public String getIntroducedBy() {
		String result =  RegressionStringUtil.getIntroducedBy(event);
		return result;
	}

	public String getType() {
		return type;
	}

	public String getARCLink() {
		return arcLink;
	}

	public long getHits() {
		return event.stats.hits;
	}

	public long getCalls() {
		return event.stats.invocations;
	}
	
	@Override
	public String toString() {
		String result = getEventSummary() + " " + getEventRate();
		return result;
	}
}
