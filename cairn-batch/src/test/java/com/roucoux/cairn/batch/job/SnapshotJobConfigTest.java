package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Snapshot;
import com.roucoux.cairn.domain.port.in.ComputeSnapshotUseCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/** The step's glue, without a Spring context: the tasklet talks to the use case only. */
class SnapshotJobConfigTest {

    @Test
    void theTaskletComputesTheSnapshotAndFinishesInOnePass() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ComputeSnapshotUseCase computeSnapshot = () -> {
            calls.incrementAndGet();
            return new Snapshot(LocalDate.of(2026, 9, 24), BigDecimal.TEN, Map.of(), Map.of());
        };
        Tasklet tasklet = new SnapshotJobConfig().snapshotTasklet(computeSnapshot);

        RepeatStatus status = tasklet.execute((StepContribution) null, (ChunkContext) null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(calls.get()).isEqualTo(1);
    }
}
