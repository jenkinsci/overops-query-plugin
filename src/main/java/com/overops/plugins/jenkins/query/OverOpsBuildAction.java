package com.overops.plugins.jenkins.query;

import com.overops.report.service.model.HtmlParts;

import hudson.model.Action;
import hudson.model.Run;

public class OverOpsBuildAction implements Action {
	
	private final Run<?, ?> build;
	private final HtmlParts htmlParts;

	OverOpsBuildAction(HtmlParts htmlParts, Run<?, ?> build) {
		this.htmlParts = htmlParts;
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

	public Run<?, ?> getBuild() {
		return build;
	}
	
	public String getHtml() {
		return htmlParts.getHtml();
	}
	
	public String getCss() {
		return htmlParts.getCss();
	}
}
