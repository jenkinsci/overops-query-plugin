package com.overops.plugins.jenkins.query;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import com.takipi.api.client.ApiClient;
import com.takipi.api.core.url.UrlClient.Response;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

//DescriptorImpl governs the global config settings

@Extension
@Symbol("OverOpsQuery")
public final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
	private String overOpsURL;
	private String overOpsSID;
	private Secret overOpsAPIKey;

	public DescriptorImpl() {
		super(QueryOverOps.class);
		load();
	}

	@Override
	public boolean isApplicable(Class<? extends AbstractProject> aClass) {
		return true;
	}

	@Override
	public String getDisplayName() {
		return "Query OverOps";
	}

	// Allows for persisting global config settings in JSONObject
	@Override
	public boolean configure(StaplerRequest req, JSONObject formData) {
		formData = formData.getJSONObject("QueryOverOps");
		overOpsURL = formData.getString("overOpsURL");
		overOpsSID = formData.getString("overOpsSID");
		overOpsAPIKey = Secret.fromString(formData.getString("overOpsAPIKey"));
		save();
		return false;
	}

	public String getOverOpsURL() {
		return overOpsURL;
	}

	public String getOverOpsSID() {
		return overOpsSID;
	}

	public Secret getOverOpsAPIKey() {
		return overOpsAPIKey;
	}

	@POST
	public FormValidation doTestConnection(@QueryParameter("overOpsURL") final String overOpsURL,
			@QueryParameter("overOpsSID") final String overOpsSID,
			@QueryParameter("overOpsAPIKey") final Secret overOpsAPIKey) {

		if (overOpsURL == null || overOpsURL.isEmpty()) {
			return FormValidation.error("OverOps URL is empty");
		}

		// Admin permission check
		Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

		try {
			String apiKey = Secret.toString(overOpsAPIKey);

			ApiClient apiClient = ApiClient.newBuilder().setHostname(overOpsURL).setApiKey(apiKey).build();
			Response<String> response = apiClient.testConnection();
			    
			boolean success = (response != null) && (!response.isBadResponse());
			
			if (success) {

				return FormValidation.ok("Connection Successful.");
			} else {
				
				int code;
				
				if (response != null) {
					code = response.responseCode;
				} else {
					code = -1;
				}

				return FormValidation.error("Unable to connect to API server. Code: " + code);
			}

		} catch (Exception e) {
			return FormValidation.error(e, "REST API error : " + e.getMessage());
		}
	}
}
