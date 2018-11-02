package com.overops.plugins.jenkins.query;

import java.util.List;

import com.overops.plugins.jenkins.query.RegressionReportBuilder.RegressionReport;
import com.takipi.api.client.util.regression.RegressionStringUtil;

import hudson.model.Action;
import hudson.model.Run;

public class OverOpsBuildAction implements Action {
	
	private static final String ISSUE = "Issue";
	
	private final Run<?, ?> build;
	private final RegressionReport regressionReport;

	OverOpsBuildAction(RegressionReport regressionReport, Run<?, ?> build) {
		this.regressionReport = regressionReport;
		this.build = build;
	}

	@Override
	public String getIconFileName() {
		return "/plugin/overops-query/images/OverOps.png";
	}

	@Override
	public String getDisplayName() {
		return "OverOps Reliability Report";
	}

	@Override
	public String getUrlName() {
		return "OverOpsReport";
	}

	public String getSummary() {

		StringBuilder result = new StringBuilder();

		int newIssues = 0;
		int severeNewIssues = 0;
		int regressions = 0;
		int severeRegressions = 0;

		for (OOReportEvent event : regressionReport.getAllIssues()) {

			String type = event.getType();

			if (type.equals(RegressionStringUtil.SEVERE_NEW)) {
				severeNewIssues++;
				continue;
			}

			if (type.equals(RegressionStringUtil.NEW_ISSUE)) {
				newIssues++;
				continue;
			}

			if (type.equals(RegressionStringUtil.SEVERE_REGRESSION)) {
				severeRegressions++;
				continue;
			}

			if (type.equals(RegressionStringUtil.REGRESSION)) {
				regressions++;
				continue;
			}
		}

		appendSummaryValue(result, RegressionStringUtil.SEVERE_NEW, severeNewIssues, true);
		appendSummaryValue(result, RegressionStringUtil.NEW_ISSUE, newIssues, true);
		appendSummaryValue(result, RegressionStringUtil.SEVERE_REGRESSION, severeRegressions, false);
		appendSummaryValue(result, RegressionStringUtil.REGRESSION, regressions, false);

		if (result.length() == 0) {
			result.append("No issues found");
		}
	
		String regName = RegressionStringUtil.getRegressionName(regressionReport.getInput(), 
			regressionReport.getRegression().getActiveWndowStart());
		
		if (regName != null) {
			result.append(" in ");
			result.append(regName);
		}

		return result.toString();
	}

	private static void appendSummaryValue(StringBuilder builder, String name, int value, boolean appendPostfix) {

		if (value > 0) {
			if (builder.length() > 0) {
				builder.append(", ");
			}

			builder.append(value);
			builder.append(" ");
			builder.append(name);
			
			if (appendPostfix) {
				builder.append(" ");
				builder.append(ISSUE);			
			}

			if (value > 1) {
				builder.append("s");
			}
		}
	}

	public List<OOReportRegressedEvent> getRegressedEvents() {
		return regressionReport.getRegressions();
	}

	public List<OOReportEvent> getNewEvents() {
		return regressionReport.getNewIssues();
	}

	public List<OOReportEvent> getAllIssues() {
		return regressionReport.getAllIssues();
	}

	public int getBuildNumber() {
		return this.build.number;
	}

	public Run<?, ?> getBuild() {
		return build;
	}
}
