package com.overops.plugins.jenkins.query;

import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.util.regression.RegressionStringUtil;

public class OOReportEvent {
	
	protected final EventResult event;
	protected final String arcLink;
	protected final String type;

	public OOReportEvent(EventResult event, String type, String arcLink) {
		this.event = event;
		this.arcLink = arcLink;
		this.type = type;
	}

	public String getEventSummary() {
		
		return RegressionStringUtil.getEventSummary(event);
	}

	public String getEventRate() {
		return RegressionStringUtil.getEventRate(event);
	}

	public String getIntroducedBy() {
		return RegressionStringUtil.getIntroducedBy(event);
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
		return getEventSummary() + " " + getEventRate();
	}
}
