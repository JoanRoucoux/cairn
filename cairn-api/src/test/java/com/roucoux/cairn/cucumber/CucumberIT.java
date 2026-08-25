package com.roucoux.cairn.cucumber;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs every {@code .feature} file under {@code src/test/resources/features/} against the glue
 * code in this package. Named {@code *IT} on purpose, exactly like {@link
 * com.roucoux.cairn.ApplicationIT}: Failsafe picks it up, Surefire does not.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.roucoux.cairn.cucumber")
public class CucumberIT {}
