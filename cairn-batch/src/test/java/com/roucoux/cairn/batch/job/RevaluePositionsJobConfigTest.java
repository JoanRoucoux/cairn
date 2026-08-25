package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.in.RevaluePositionUseCase;
import com.roucoux.cairn.domain.port.out.LoadOpenPositionsPort;
import com.roucoux.cairn.domain.port.out.SavePositionPort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.transaction.PlatformTransactionManager;

/** The step's glue, without a Spring context: each piece talks to a port or a use case only. */
@ExtendWith(MockitoExtension.class)
class RevaluePositionsJobConfigTest {

    private final RevaluePositionsJobConfig config = new RevaluePositionsJobConfig();

    @Mock
    private LoadOpenPositionsPort loadOpenPositions;

    @Mock
    private RevaluePositionUseCase revaluePosition;

    @Mock
    private SavePositionPort savePosition;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private static Position position() {
        return Position.open("US0378331005", BigDecimal.ONE, new BigDecimal("100.00"));
    }

    @Test
    void theJobAndItsStepKeepTheNamesOperationsScheduleOn() {
        Step step = config.revaluePositionsStep(
                jobRepository,
                transactionManager,
                config.openPositionsReader(loadOpenPositions),
                config.revaluePositionProcessor(revaluePosition),
                config.positionWriter(savePosition));

        assertThat(step.getName()).isEqualTo("revaluePositionsStep");
        assertThat(config.revaluePositionsJob(jobRepository, step).getName()).isEqualTo("revaluePositionsJob");
    }

    @Test
    void theReaderStreamsTheOpenPositionsThenStops() throws Exception {
        Position position = position();
        when(loadOpenPositions.findOpen()).thenReturn(List.of(position));

        var reader = config.openPositionsReader(loadOpenPositions);

        assertThat(reader.read()).isEqualTo(position);
        assertThat(reader.read()).isNull();
    }

    @Test
    void theProcessorDelegatesToTheUseCase() throws Exception {
        Position position = position();
        Position revalued = position.revalue(new BigDecimal("123.45"));
        when(revaluePosition.revalue(position)).thenReturn(revalued);

        assertThat(config.revaluePositionProcessor(revaluePosition).process(position))
                .isEqualTo(revalued);
    }

    @Test
    void theWriterSavesEveryItemOfTheChunk() throws Exception {
        Position first = position();
        Position second = position();

        config.positionWriter(savePosition).write(Chunk.of(first, second));

        verify(savePosition).save(first);
        verify(savePosition).save(second);
    }
}
