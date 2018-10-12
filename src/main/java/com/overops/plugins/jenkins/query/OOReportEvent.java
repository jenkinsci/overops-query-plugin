package com.overops.plugins.jenkins.query;

import java.text.DecimalFormat;
import java.util.regex.Pattern;

import com.takipi.common.api.result.event.EventResult;

public class OOReportEvent {
	
	protected static final DecimalFormat decimalFormat = new DecimalFormat("#.00"); 
	
	public static final String NEW_ISSUE = "New";
	public static final String SEVERE_NEW = "Severe New";
	public static final String REGRESSION = "Regression";
	public static final String SEVERE_REGRESSION = "Severe Regression";
	
	protected final EventResult event;
	protected final String arcLink;
	protected final String type;

	public OOReportEvent(EventResult event, String type, String arcLink) {
		this.event = event;
		this.arcLink = arcLink;
		this.type = type;
	}

	public String getEventSummary() {
		
		String[] parts = event.error_location.class_name.split(Pattern.quote("."));
		
		String simpleClassName;
		
		if (parts.length > 0) {
			simpleClassName = parts[parts.length - 1];
		} else {
			simpleClassName = event.error_location.class_name;
		}
		
;		return event.name + " in " + simpleClassName + "." + event.error_location.method_name;
	}

	public String getEventRate() {
		StringBuilder result = new StringBuilder();
		
		if ((event.stats.invocations == 0) || (event.stats.hits == 0 )) {
			return "1";
		}
		
		double rate = (double)event.stats.hits / (double)event.stats.invocations * 100; 	
		
		result.append(event.stats.hits);
		result.append("/");
		result.append(event.stats.invocations);
		result.append(" (");
		String fmt = decimalFormat.format(rate);
		if (fmt.startsWith(".")) {
			result.append("0");
		}
 		result.append(fmt);
		result.append("%)");
		
		return result.toString();
	}

	public String getIntroducedBy() {
		return event.introduced_by;
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
}
