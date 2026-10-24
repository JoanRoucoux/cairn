package com.roucoux.cairn.domain.model;

public record StartCommand(long chatId, String label, String payload) {}
