package com.roucoux.cairn.domain.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class CapturedLog implements AutoCloseable {

    private final Logger logger;
    private final boolean usedParentHandlers;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    private CapturedLog(Class<?> type) {
        this.logger = Logger.getLogger(type.getName());
        this.usedParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    static CapturedLog of(Class<?> type) {
        return new CapturedLog(type);
    }

    List<LogRecord> records() {
        return records;
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setUseParentHandlers(usedParentHandlers);
    }
}
