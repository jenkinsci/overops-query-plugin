# OverOps Reliability Report - Jenkins Plugin

- [Installation](#installation)
- [Global Configuration](#global-configuration)
- [Job Post Build Configuration](#job-post-build-configuration)
- [Pipeline](#pipeline)
- [Troubleshooting](#troubleshooting)

This plugin provides a mechanism for applying OverOps severity assignment and regression analysis to new builds to allow application owners, DevOps engineers, and SREs to determine the quality of their code before promoting it into production.

Run this plugin as a post build step after all other testing is complete to generate a Reliability Report that will determine the stability of the build. From the Reliability Report you can drill down into each specific error using the OverOps [Automated Root Cause](https://doc.overops.com/docs/automated-root-cause-arc) analysis screen to solve the issue.

For more information about this plugin and [quality gates](https://doc.overops.com/docs/overops-quality-gates), see the [OverOps CI/CD Pipeline Integrations guide](https://doc.overops.com/docs/cicd-pipeline).

![OverOps Reliability Report](readme/quality-report.png)

## Installation

Prerequisites

- Jenkins 2.43 running on Java 1.8 or later
- OverOps installed on the designated environment

Install the OverOps Query Plugin through the Plugin Manager. From the Jenkins Dashboard, select Manage Jenkins &rarr; Manage Plugins &rarr; Available &rarr;
scroll down to **OverOps Query Plugin**.

## Global Configuration

After installing the plugin, configure it to connect to OverOps.

From the Jenkins Dashboard, select Manage Jenkins &rarr; Configure System &rarr;
scroll down to **OverOps Query Plugin**.

![configure system](readme/configure.png)

### OverOps URL

The complete URL of the OverOps API, including protocol and port. (e.g. `https://api.overops.com` for SaaS or `http://host.domain.com:8080` for on prem).

### OverOps Environment ID

The default OverOps environment identifier (e.g. S12345) if none is specified in the build settings.

### OverOps API Token

The OverOps REST API token to use for authentication. This can be obtained from the OverOps dashboard under Settings &rarr; Account.

#### Testing

Click *Test Connection* to show a count of available metrics. If the count shows 0 measurements, credentials are correct but database may be wrong. If credentials are incorrect you will receive an authentication error.

## Job Post Build Configuration

Choose a project, then select Configure &rarr; Post-build Actions &rarr; scroll down to **Query OverOps**

![code quality gate options](readme/code-quality-gate-options.png)

### Application Name

*(Optional)* [Application Name](https://doc.overops.com/docs/naming-your-application-server-deployment) as specified in OverOps

- If populated, the plugin will filter the data for the specific application in OverOps.
- If blank, no application filter will be applied in query.

**Example:**  
\${JOB\_NAME }

### Deployment Name

*(Optional)* [Deployment Name](https://doc.overops.com/docs/naming-your-application-server-deployment) as specified in OverOps or use Jenkins environment variables.

**Example:**  
\${BUILD\_NUMBER} or \${JOB\_NAME }\-\${BUILD\_NUMBER}

- If populated, the plugin will filter the data for the specific deployment name in OverOps
- If blank, no deployment filter will be applied in the query.

### Environment ID

The OverOps environment identifier (e.g S4567) to inspect data for this build. If no value is provided here, the value provided in the global Jenkins plugin settings will be used.

### Regex Filter

A way to filter out specific event types from affecting the outcome of the OverOps Reliability report.

- Sample list of event types, Uncaught Exception, Caught Exception,|Swallowed Exception, Logged Error, Logged Warning, Timer
- This filter enables the removal of one or more of these event types from the final results.
- Example filter expression with pipe separated list- ```"type":"s*(Logged Error|Logged Warning|Timer)"```

### Mark Build Unstable

If checked the build will be marked unstable if any of the above gates are met.

### Show Events For Passed Gates

If checked all events will be displayed, regardless if the gate has passed or failed.

### Show Top Issues

Prints the top X events (as provided by this parameter) with the highest volume of errors detected in the current build. This is used in conjunction with Max Error Volume and Unique Error Volume to identify the errors which caused a build to fail.

### New Error Gate

Detect all new errors in the build. If found, the build will be marked as unstable.

### Resurfaced Error Gate

Detect all resurfaced errors in the build. If found, the build will be marked as unstable.

### Total Error Volume Gate

Set the max total error volume allowed. If exceeded the build will be marked as unstable.

### Unique Error Volume Gate

Set the max unique error volume allowed. If exceeded the build will be marked as unstable.

### Critical Exception Type Gate

A comma delimited list of exception types that are deemed as severe regardless of their volume. If any events of any exceptions listed have a count greater than zero, the build will be marked as unstable.

**Example:**  
```NullPointerException,IndexOutOfBoundsException```

### Debug Mode

If checked, all queries and results will be displayed in the OverOps reliability report. *For debugging purposes only*.

### Mark build successful if unable to generate a Quality Report

If checked, the build will be marked **Success** if unable to generate a Quality Report. By default, the build will be marked **Not Built** if unable to generate a Quality Report.

### Link Report
If set to true a link to the Quality Report will be generated and displayed instead of the Quality Report itself. The OverOps Application URL needs to be set for the link to be generated correctly. Also this option, if set to true, voids the Mark build successful if unstable option. This link can be used if it is desired to not fail the build and you do not want to wait for the Quality Report to be generated.

## Pipeline

This plugin is compatible with Jenkins Pipeline.

```groovy
stage('OverOps') {
  steps {
    OverOpsQuery(
      // build configuration
      applicationName: '${JOB_NAME}',
      deploymentName: '${JOB_NAME}-${BUILD_NUMBER}',
      serviceId: 'Sxxxxx',

      // filter out event types
      regexFilter: '"type":\\"*(Timer|Logged Warning)',

      // mark build unstable
      markUnstable: true,

      // show events if gate passes or fails
      showPassedGateEvents: false,

      // show top X issues
      printTopIssues: 5,

      // new error gate
      newEvents: true,

      // resurfaced error gate
      resurfacedErrors: true,

      // total error volume gate
      maxErrorVolume: 0,

      // unique error volume gate
      maxUniqueErrors: 0,

      // critical exception type gate
      criticalExceptionTypes: 'NullPointerException,IndexOutOfBoundsException,InvalidCastException,AssertionError',

      // debug mode
      debug: false,

      // if true, mark build SUCCESS if unable to generate report
      // if false, mark build NOT_BUILT if unable to generate report
      errorSuccess: false
    )
    echo "OverOps Reliability Report: ${BUILD_URL}OverOpsReport/"
  }
}
```

### Parameters

All parameters are optional.

| Parameter | Type | Default Value |
|---------|------|---------------|
| [`applicationName`](#application-name) | String | `null` |
| [`deploymentName`](#deployment-name) | String | `null` |
| [`serviceId`](#environment-id) | String | `null` |
| [`regexFilter`](#regex-filter) | String | `null` |
| [`markUnstable`](#mark-build-unstable) | boolean | `false` |
| [`showPassedGateEvents`](#show-events-for-passed-gates) | boolean | `false` |
| [`printTopIssues`](#show-top-issues) | Integer | `5` |
| [`newEvents`](#new-error-gate) | boolean | `false` |
| [`resurfacedErrors`](#resurfaced-error-gate) | boolean | `false` |
| [`maxErrorVolume`](#total-error-volume-gate) | Integer | `0` |
| [`maxUniqueErrors`](#unique-error-volume-gate) | Integer | `0` |
| [`criticalExceptionTypes`](#critical-exception-type-gate) | String | `null` |
| [`debug`](#debug-mode) | boolean | `false` |
| [`errorSuccess`](#mark-build-successful-if-unable-to-generate-a-quality-report) | boolean | `false` |

### Migrating from v2 to v3

Starting in v3, all increasing gate settings have been removed and must be removed from your Jenkinsfile. See [Breaking Changes](#breaking-changes) below.

### Migrating from v1 to v2

Starting in v2, all parameters are optional. You may remove any parameters from your Jenkinsfile which are set to the default value.

#### Breaking Changes

- In v3, all increasing gate settings have been removed.

| Parameter | Notes |
|---|---|
| `activeTimespan` | Removed |
| `baselineTimespan` | Removed |
| `minVolumeThreshold` | Removed |
| `minErrorRateThreshold` | Removed |
| `regressionDelta` | Removed |
| `criticalRegressionDelta` | Removed |
| `applySeasonality` | Removed |

- In v2, `activeTimespan` and `baselineTimespan` are now Strings, not Integers. In v1, these values were time in minutes. In v2, append `m` for minutes, `h` for hours, and `d` for days.

    > *For example:*  
    > `10080` (int, in minutes) &rarr; `'10080m'` or `'168h'` or `'7d'`

- The `verbose` parameter has been renamed to `debug`.

- The `serverWait` and `showResults` parameters have been removed.

| Parameter | v1 | v2 | Notes |
|---|-----|-----|---|
|`activeTimespan`|`10080`|`'7d'`| Now a String |
|`baselineTimespan`|`720`|`'12h'`| Now a String |
|`verbose`|`false`| | Replaced by `debug`|
|`debug`| |`false`|Previously `verbose`|
|`serverWait`|`60`| | Removed |
|`showResults`|`true`| | Removed |

## Troubleshooting

If previous build steps were not successful, the plugin will not run or attempt to generate a Quality Report.

If the plugin runs but is not able to generate a Quality Report, the build will be marked **Not Built** by default or **Success** if [Mark build successful if unable to generate a Quality Report](#mark-build-successful-if-unable-to-generate-a-quality-report) is checked.

![configure system](readme/error-report.png)

For short-lived applications, [we recommend](https://support.overops.com/hc/en-us/articles/360041054474-Best-Practice-Short-lived-application-considerations) using the ```-Dtakipi.shutdown.gracetime=20000``` agent property.
