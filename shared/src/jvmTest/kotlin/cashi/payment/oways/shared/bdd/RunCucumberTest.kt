package cashi.payment.oways.shared.bdd

import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/** Runs the Gherkin features in `src/jvmTest/resources/features` with the step definitions in this package. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
class RunCucumberTest