package com.overops.plugins.jenkins.query;

import java.util.List;

import com.overops.plugins.jenkins.query.ReportBuilder.QualityReport;
import com.takipi.api.client.util.cicd.OOReportEvent;

import hudson.model.Action;
import hudson.model.Run;

public class OverOpsBuildAction implements Action {
	
	private static final String ISSUE = "Issue";
	private static final String STRING_FORMAT = "%,d";
	
	private final Run<?, ?> build;
	private final QualityReport qualityReport;

	OverOpsBuildAction(QualityReport qualityReport, Run<?, ?> build) {
		this.qualityReport = qualityReport;
		this.build = build;
	}

	@Override
	public String getIconFileName() {
		return "/plugin/overops-query/images/OverOps.png";
	}

	@Override
	public String getDisplayName() {
		return "OverOps Quality Report";
	}

	@Override
	public String getUrlName() {
		return "OverOpsReport";
	}
	
	public boolean getUnstable() {
		return qualityReport.getUnstable();
	}
	
	public boolean getMarkedUnstable() {
		return qualityReport.isMarkedUnstable();
	}
	
	public String getSummary() {
		if (getUnstable() && getMarkedUnstable()) {
			//the build is unstable when marking the build as unstable
			return "OverOps has marked build "+ getDeploymentName() + " as unstable.";
		} else if (!getMarkedUnstable() && getUnstable()) {
			//unstable build stable when NOT marking the build as unstable
			return "OverOps has detected issues with build "+ getDeploymentName() + "  but did not mark the build as unstable.";
		} else {
			//stable build when marking the build as unstable
			return "Congratulations, build " + getDeploymentName() + " has passed all quality gates!";
		} 
	}
	
	private String getDeploymentName() {
		String value = qualityReport.getInput().deployments.toString();	
		value = value.replace("[", "");
		value = value.replace("]", "");
		return value;
	}
	
	public boolean getPassedNewErrorGate() {
		if (getCheckNewEvents() && !getNewErrorsExist()) {
			return true;
		}
		
		return false;
	}
	
	public boolean getCheckNewEvents() {
		return qualityReport.isCheckNewGate();
	}
	
	public String getNewErrorSummary() {
		if (getNewEvents() != null && getNewEvents().size() > 0) {
			int count = qualityReport.getNewIssues().size();
			StringBuilder sb = new StringBuilder("New Error Gate: Failed, OverOps detected ");
			sb.append(count);
			sb.append(" new error");
			if (count != 1) {
				sb.append("s");
			}
			sb.append(" in your build.");
			return sb.toString();
		} else if (qualityReport.isCheckNewGate()) {
			return "New Error Gate: Passed, OverOps did not detect any new errors in your build.";
		}
		
		return null;
	}
	
	public boolean getNewErrorsExist() {
		if (getNewEvents() != null && getNewEvents().size() > 0) {
			return true;
		}
		return false;
	}
	
	public List<OOReportEvent> getNewEvents() {
		return qualityReport.getNewIssues();
	}
	
	public boolean getPassedResurfacedErrorGate() {
		if (getCheckResurfacedEvents() && !getResurfacedErrorsExist()) {
			return true;
		}
		
		return false;
	}
	
	public boolean getResurfacedErrorsExist() {
		if (getResurfacedEvents() != null && getResurfacedEvents().size() > 0) {
			return true;
		}
		return false;
	}
	
	public boolean getCheckResurfacedEvents() {
		return qualityReport.isCheckResurfacedGate();
	}
	
	public String getResurfacedErrorSummary() {
		if (getResurfacedEvents() != null && getResurfacedEvents().size() > 0) {
			return "Resurfaced Error Gate: Failed, OverOps detected " + qualityReport.getResurfacedErrors().size() + " resurfaced errors in your build.";
		} else if (qualityReport.isCheckResurfacedGate()) {
			return "Resurfaced Error Gate: Passed, OverOps did not detect any resurfaced errors in your build.";
		}
		
		return null;
	}
	
	public List<OOReportEvent> getResurfacedEvents() {
		return qualityReport.getResurfacedErrors();
	}
	
	public boolean getCheckCriticalErrors() {
		return qualityReport.isCheckCriticalGate();
	}
	
	public boolean getPassedCriticalErrorGate() {
		if (getCheckCriticalErrors() && !getCriticalErrorsExist()) {
			return true;
		}
		
		return false;
	}
	
	public boolean getCriticalErrorsExist() {
		if (getCriticalEvents() != null && getCriticalEvents().size() > 0) {
			return true;
		}
		return false;
	}
	
	public String getCriticalErrorSummary() {
		if (getCriticalEvents() != null && getCriticalEvents().size() > 0) {
			return "Critical Error Gate: Failed, OverOps detected " + qualityReport.getCriticalErrors().size() + " critical errors in your build.";
		} else if (qualityReport.isCheckCriticalGate()) {
			return "Critical Error Gate: Passed, OverOps did not detect any critical errors in your build.";
		}
		
		return null;
	}
	
	public List<OOReportEvent> getCriticalEvents() {
		return qualityReport.getCriticalErrors();
	}
	
	//this will serve as a check for either unique or total error gates
	public boolean getCountGates() {
		if (getCheckUniqueErrors() || getCheckTotalErrors()) {
			return true;
		}
		return false;
	}
	
	public boolean getCheckTotalErrors() {
		return qualityReport.isCheckVolumeGate();
	}
	
	public boolean getPassedTotalErrorGate() {
		if (getCheckTotalErrors() && (qualityReport.getEventVolume() > 0 && qualityReport.getEventVolume() < qualityReport.getMaxEventVolume())) {
			return true;
		}
		
		return false;
	}
	
	public String getTotalErrorSummary() {
		if (qualityReport.getEventVolume() > 0 && qualityReport.getEventVolume() >= qualityReport.getMaxEventVolume()) {
			return "Total Error Volume Gate: Failed, OverOps detected " + qualityReport.getEventVolume() + " total errors which is >= the max allowable of " + qualityReport.getMaxEventVolume();
		} else if (qualityReport.getEventVolume() > 0 && qualityReport.getEventVolume() < qualityReport.getMaxEventVolume()) {
			return "Total Error Volume Gate: Passed, OverOps detected " + qualityReport.getEventVolume() + " total errors which is < than max allowable of " + qualityReport.getMaxEventVolume();
		}
		
		return null;
	}
	
	public boolean getCheckUniqueErrors() {
		return qualityReport.isCheckUniqueGate();
	}
	
	public boolean getHasTopErrors() {
		if (!getPassedTotalErrorGate() || !getPassedUniqueErrorGate()) {
			return true;
		}
		return false;
	}
	
	public boolean getPassedUniqueErrorGate() {
		if (getCheckUniqueErrors() && (qualityReport.getUniqueEventsCount() > 0 && qualityReport.getUniqueEventsCount() < qualityReport.getMaxUniqueVolume())) {
			return true;
		}
		
		return false;
	}
	
	public String getUniqueErrorSummary() {
		if (qualityReport.getUniqueEventsCount() > 0 && qualityReport.getUniqueEventsCount() >= qualityReport.getMaxUniqueVolume()) {
			return "Unique Error Volume Gate: Failed, OverOps detected " + qualityReport.getUniqueEventsCount() + " unique errors which is >= the max allowable of " + qualityReport.getMaxUniqueVolume();
		} else if (qualityReport.getUniqueEventsCount() > 0 && qualityReport.getUniqueEventsCount() < qualityReport.getMaxUniqueVolume()) {
			return "Unique Error Volume Gate: Passed, OverOps detected " + qualityReport.getUniqueEventsCount() + " unique errors which is < than max allowable of " + qualityReport.getMaxUniqueVolume();
		}
		
		return null;
	}
	
	public List<OOReportEvent> getTopEvents() {
		return qualityReport.getTopErrors();
	}
	
	public String getRegressionSumarry() {
		if (!getPassedRegressedEvents()) {
			return "Increasing Quality Gate: Failed, OverOps detected increasing errors in the current build against the baseline of " + qualityReport.getInput().baselineTime;
		} else if (getPassedRegressedEvents()) {
			return "Increasing Quality Gate: Passed, OverOps did not detect any increasing errors in the current build against the baseline of " + qualityReport.getInput().baselineTime;
		}
		
		return null;
	}
	
	public boolean getCheckRegressedErrors() {
		return qualityReport.isCheckRegressionGate();
	}
	
	public boolean getPassedRegressedEvents() {
		if (getCheckRegressedErrors() && qualityReport.getRegressions() != null && qualityReport.getRegressions().size() > 0) {
			return false;
		}
		return true;
	}

	public List<OOReportEvent> getRegressedEvents() {
		return qualityReport.getAllIssues();
	}

	public Run<?, ?> getBuild() {
		return build;
	}

	public String getNewGateTotal() {
		return String.format(STRING_FORMAT, qualityReport.getNewIssues().size());
	}

	public String getResurfacedGateTotal() {
		return String.format(STRING_FORMAT, qualityReport.getResurfacedErrors().size());
	}

	public String getCriticalGateTotal() {
		return String.format(STRING_FORMAT, qualityReport.getCriticalErrors().size());
	}

	public String getTotalGateTotal() {
		return String.format(STRING_FORMAT, qualityReport.getEventVolume());
	}

	public String getUniqueGateTotal() {
		return String.format(STRING_FORMAT, qualityReport.getUniqueEventsCount());
	}

	public String getRegressionGateTotal() {
		return String.format(STRING_FORMAT, qualityReport.getRegressions() != null ? qualityReport.getRegressions().size() : 0);
	}

}
