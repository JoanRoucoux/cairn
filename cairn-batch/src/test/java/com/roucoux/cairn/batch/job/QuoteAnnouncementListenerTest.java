package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;

/** No Spring context: the listener talks to a use case only, driven straight through its callbacks. */
class QuoteAnnouncementListenerTest {

    private static final Quote QUOTE_1 = quote("456.78");
    private static final Quote QUOTE_2 = quote("123.45");

    @Test
    void buffersWrittenQuotesAndAnnouncesThemOnlyOnceTheChunkCommits() {
        RecordingAnnounceUseCase announce = new RecordingAnnounceUseCase();
        QuoteAnnouncementListener listener = new QuoteAnnouncementListener(announce);

        listener.afterWrite(Chunk.of(QUOTE_1, QUOTE_2));
        assertThat(announce.savedQuotes()).isEmpty();

        listener.afterChunk(chunkContext());

        assertThat(announce.savedQuotes()).containsExactly(List.of(QUOTE_1, QUOTE_2));
    }

    @Test
    void aChunkThatRollsBackAnnouncesNothingAndDiscardsItsBuffer() {
        RecordingAnnounceUseCase announce = new RecordingAnnounceUseCase();
        QuoteAnnouncementListener listener = new QuoteAnnouncementListener(announce);

        listener.afterWrite(Chunk.of(QUOTE_1));
        listener.afterChunkError(chunkContext());
        // TaskletStep never calls afterChunk once afterChunkError has fired for the same chunk;
        // calling it here proves the buffer was actually cleared, not merely never flushed.
        listener.afterChunk(chunkContext());

        assertThat(announce.savedQuotes()).isEmpty();
    }

    @Test
    void aChunkThatWroteNothingAnnouncesNothing() {
        RecordingAnnounceUseCase announce = new RecordingAnnounceUseCase();
        QuoteAnnouncementListener listener = new QuoteAnnouncementListener(announce);

        listener.afterChunk(chunkContext());

        assertThat(announce.savedQuotes()).isEmpty();
    }

    @Test
    void announcesTheRefreshCompletionOnceWithTheStepsCountersAndTheJobsAssetClasses() {
        RecordingAnnounceUseCase announce = new RecordingAnnounceUseCase();
        QuoteAnnouncementListener listener = new QuoteAnnouncementListener(announce);
        StepExecution stepExecution = stepExecutionOf("ETF,CRYPTO");
        stepExecution.setWriteCount(18);
        stepExecution.setReadSkipCount(1);
        stepExecution.setProcessSkipCount(2);
        stepExecution.setWriteSkipCount(0);

        listener.afterStep(stepExecution);

        assertThat(announce.completions())
                .containsExactly(
                        new Completion(Set.of(AssetClass.ETF, AssetClass.CRYPTO), 18, 3, RefreshTrigger.BATCH));
    }

    private static ChunkContext chunkContext() {
        return new ChunkContext(new StepContext(stepExecutionOf("ETF")));
    }

    private static StepExecution stepExecutionOf(String assetClasses) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("assetClasses", assetClasses)
                .toJobParameters();
        JobInstance instance = new JobInstance(1L, "refreshQuotesJob");
        JobExecution jobExecution = new JobExecution(1L, instance, parameters);
        return new StepExecution("refreshQuotesStep", jobExecution);
    }

    private static Quote quote(String price) {
        return new Quote(
                UUID.randomUUID(), LocalDate.now(), new BigDecimal(price), "EUR", PriceSource.YAHOO, Instant.now());
    }

    private record Completion(Set<AssetClass> assetClasses, int refreshed, int failed, RefreshTrigger trigger) {}

    private static final class RecordingAnnounceUseCase implements AnnounceQuotesUseCase {
        private final List<List<Quote>> savedQuotes = new ArrayList<>();
        private final List<Completion> completions = new ArrayList<>();

        @Override
        public void quotesSaved(List<Quote> quotes) {
            savedQuotes.add(quotes);
        }

        @Override
        public void refreshCompleted(Set<AssetClass> assetClasses, int refreshed, int failed, RefreshTrigger trigger) {
            completions.add(new Completion(assetClasses, refreshed, failed, trigger));
        }

        List<List<Quote>> savedQuotes() {
            return savedQuotes;
        }

        List<Completion> completions() {
            return completions;
        }
    }
}
