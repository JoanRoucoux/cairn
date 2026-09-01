package com.roucoux.cairn.application.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioCsvReaderTest {

    private final PortfolioCsvReader reader = new PortfolioCsvReader();

    @Test
    void reportsEveryUnreadableRowAtOnceRatherThanThrowingOnTheFirst() {
        String csv = PortfolioCsvReader.HEADER + "\r\n"
                + "Sample Broker,NOT_A_TYPE,Sample Bank,Tracker,LU0000000001,100,20.00\r\n"
                + "Sample Broker,PEA,Sample Bank,Tracker,LU0000000002,not-a-number,20.00\r\n";

        assertThatThrownBy(() -> reader.read(csv))
                .asInstanceOf(type(PortfolioImportRejectedException.class))
                .extracting(PortfolioImportRejectedException::errors)
                .asInstanceOf(list(ImportError.class))
                .extracting(ImportError::rowIndex)
                .containsExactly(0, 1);
    }

    @Test
    void refusesAFileWhoseHeaderIsNotTheTemplate() {
        String csv = "account,quantity\r\nSample Broker,100\r\n";

        assertThatThrownBy(() -> reader.read(csv)).isInstanceOf(PortfolioImportRejectedException.class);
    }

    @Test
    void readsAWellFormedFileIntoRows() {
        String csv = PortfolioCsvReader.HEADER + "\r\n" + "Sample Broker,PEA,Sample Bank,Global Growth Tracker,"
                + "LU0000000001,100,20.00\r\n";

        List<ImportRow> rows = reader.read(csv);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.accountName()).isEqualTo("Sample Broker");
            assertThat(row.accountType()).isEqualTo(AccountType.PEA);
            assertThat(row.institution()).isEqualTo("Sample Bank");
            assertThat(row.instrumentName()).isEqualTo("Global Growth Tracker");
            assertThat(row.isinOrTicker()).isEqualTo("LU0000000001");
            assertThat(row.quantity()).isEqualByComparingTo("100");
            assertThat(row.averageCost()).isEqualByComparingTo("20.00");
        });
    }
}
