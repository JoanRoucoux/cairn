package com.roucoux.cairn.cucumber;

import io.cucumber.java.Before;

/** Resets shared fixtures between scenarios, so stubs from one never leak into the next. */
public class Hooks {

    @Before
    public void resetMarketData() {
        CucumberSpringConfiguration.MARKET_DATA.resetAll();
    }
}
