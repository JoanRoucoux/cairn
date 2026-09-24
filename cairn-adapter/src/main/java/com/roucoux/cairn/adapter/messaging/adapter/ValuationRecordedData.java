package com.roucoux.cairn.adapter.messaging.adapter;

import java.math.BigDecimal;
import java.time.Instant;

record ValuationRecordedData(Instant at, BigDecimal totalEur, BigDecimal dayChangeEur) {}
