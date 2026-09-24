package com.roucoux.cairn.batch.job;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.stereotype.Component;

@Component
@StepScope
class QuoteAnnouncementListener implements ItemWriteListener<Quote>, ChunkListener, StepExecutionListener {

    private final AnnounceQuotesUseCase announceQuotes;
    private final List<Quote> writtenSinceLastFlush = new ArrayList<>();

    QuoteAnnouncementListener(AnnounceQuotesUseCase announceQuotes) {
        this.announceQuotes = announceQuotes;
    }

    @Override
    public void afterWrite(Chunk<? extends Quote> items) {
        writtenSinceLastFlush.addAll(items.getItems());
    }

    @Override
    public void afterChunk(ChunkContext context) {
        if (writtenSinceLastFlush.isEmpty()) {
            return;
        }
        announceQuotes.quotesSaved(List.copyOf(writtenSinceLastFlush));
        writtenSinceLastFlush.clear();
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        writtenSinceLastFlush.clear();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        announceQuotes.refreshCompleted(
                assetClassesOf(stepExecution),
                (int) stepExecution.getWriteCount(),
                (int) stepExecution.getSkipCount(),
                RefreshTrigger.BATCH);
        return null;
    }

    private static Set<AssetClass> assetClassesOf(StepExecution stepExecution) {
        String assetClasses = stepExecution.getJobParameters().getString("assetClasses");
        return Arrays.stream(assetClasses.split(","))
                .map(String::trim)
                .map(AssetClass::valueOf)
                .collect(Collectors.toSet());
    }
}
