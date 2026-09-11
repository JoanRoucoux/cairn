package com.roucoux.cairn.application.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioCsvReaderTest {

    private final PortfolioCsvReader reader = new PortfolioCsvReader();

    @Test
    void reportsEveryUnreadableRowAtOnceRatherThanThrowingOnTheFirst() {
        String csv = PortfolioCsvReader.HEADER + "\r\n"
                + "Sample Broker;NOT_A_TYPE;Sample Bank;Tracker;LU0000000001;100;20.00\r\n"
                + "Sample Broker;PEA;Sample Bank;Tracker;LU0000000002;not-a-number;20.00\r\n";

        assertThatThrownBy(() -> reader.read(csv))
                .asInstanceOf(type(ImportFileRejectedException.class))
                .extracting(ImportFileRejectedException::errors)
                .asInstanceOf(list(LineError.class))
                .extracting(LineError::line, LineError::code, LineError::value)
                .containsExactly(
                        tuple(2, ImportErrorCode.UNKNOWN_ACCOUNT_TYPE, "NOT_A_TYPE"),
                        tuple(3, ImportErrorCode.NOT_A_NUMBER, "not-a-number"));
    }

    @Test
    void refusesAFileWhoseHeaderIsNotTheTemplate() {
        String csv = "account;quantity\r\nSample Broker;100\r\n";

        assertThatThrownBy(() -> reader.read(csv))
                .asInstanceOf(type(ImportFileRejectedException.class))
                .extracting(ImportFileRejectedException::errors)
                .asInstanceOf(list(LineError.class))
                .singleElement()
                .extracting(LineError::line, LineError::code)
                .containsExactly(1, ImportErrorCode.BAD_HEADER);
    }

    @Test
    void refusesTheHeaderOfACommaSeparatedFile() {
        String csv = "account,accountType,institution,instrument,isinOrTicker,quantity,averageCost\r\n";

        assertThatThrownBy(() -> reader.read(csv))
                .asInstanceOf(type(ImportFileRejectedException.class))
                .extracting(ImportFileRejectedException::errors)
                .asInstanceOf(list(LineError.class))
                .singleElement()
                .extracting(LineError::code)
                .isEqualTo(ImportErrorCode.BAD_HEADER);
    }

    @Test
    void refusesARowThatDoesNotHaveEveryColumn() {
        String csv = PortfolioCsvReader.HEADER + "\r\nSample Broker;PEA;Sample Bank\r\n";

        assertThatThrownBy(() -> reader.read(csv))
                .asInstanceOf(type(ImportFileRejectedException.class))
                .extracting(ImportFileRejectedException::errors)
                .asInstanceOf(list(LineError.class))
                .singleElement()
                .extracting(LineError::code)
                .isEqualTo(ImportErrorCode.WRONG_COLUMN_COUNT);
    }

    @Test
    void readsAWellFormedFileIntoRows() {
        String csv = PortfolioCsvReader.HEADER + "\r\n"
                + "Sample Broker;PEA;Sample Bank;Global Growth Tracker;LU0000000001;100;20.00\r\n";

        ImportFile file = reader.read(csv);

        assertThat(file.rows()).singleElement().satisfies(row -> {
            assertThat(row.accountName()).isEqualTo("Sample Broker");
            assertThat(row.accountType()).isEqualTo(AccountType.PEA);
            assertThat(row.institution()).isEqualTo("Sample Bank");
            assertThat(row.instrumentName()).isEqualTo("Global Growth Tracker");
            assertThat(row.isinOrTicker()).isEqualTo("LU0000000001");
            assertThat(row.quantity()).isEqualByComparingTo("100");
            assertThat(row.averageCost()).isEqualByComparingTo("20.00");
        });
    }

    @Test
    void readsBackTheTemplateItHandsOut() {
        String csv = PortfolioCsvReader.TEMPLATE
                + "Sample Broker;PEA;Sample Bank;Global Growth Tracker;LU0000000001;100;20.00\r\n";

        assertThat(reader.read(csv).rows())
                .singleElement()
                .satisfies(row -> assertThat(row.accountName()).isEqualTo("Sample Broker"));
    }

    @Test
    void handsOutATemplateWhoseExamplesAreNeverImported() {
        assertThat(PortfolioCsvReader.TEMPLATE.lines().filter(line -> line.startsWith("#")))
                .isNotEmpty();
        assertThat(reader.read(PortfolioCsvReader.TEMPLATE).rows()).isEmpty();
    }

    @Test
    void numbersARefusedRowByItsLineInTheFileCountingTheLinesItSkipped() {
        String csv = PortfolioCsvReader.HEADER + "\r\n"
                + "# Sample Broker;PEA;Sample Bank;Tracker;LU0000000001;100;20.00\r\n"
                + "\r\n"
                + "Sample Broker;NOT_A_TYPE;Sample Bank;Tracker;LU0000000001;100;20.00\r\n";

        assertThatThrownBy(() -> reader.read(csv))
                .asInstanceOf(type(ImportFileRejectedException.class))
                .extracting(ImportFileRejectedException::errors)
                .asInstanceOf(list(LineError.class))
                .singleElement()
                .extracting(LineError::line)
                .isEqualTo(4);
    }

    @Test
    void locatesTheDomainsRefusalsOnTheLinesTheirRowsCameFrom() {
        ImportFile file = reader.read(PortfolioCsvReader.HEADER + "\r\n"
                + "# Sample Broker;PEA;Sample Bank;Tracker;LU0000000001;100;20.00\r\n"
                + "Sample Broker;PEA;Sample Bank;Tracker;LU0000000001;100;20.00\r\n"
                + "\r\n"
                + "Sample Broker;PEA;Sample Bank;Other;LU0000000002;5;\r\n");
        PortfolioImportRejectedException refused = new PortfolioImportRejectedException(
                List.of(new ImportError(1, ImportErrorCode.UNRESOLVED_INSTRUMENT, "LU0000000002")));

        assertThat(file.locate(refused).errors())
                .extracting(LineError::line, LineError::code, LineError::value)
                .containsExactly(tuple(5, ImportErrorCode.UNRESOLVED_INSTRUMENT, "LU0000000002"));
    }

    @Test
    void keepsACommaInsideAFieldRatherThanSplittingOnIt() {
        String csv = PortfolioCsvReader.HEADER + "\r\n"
                + "Fortuneo, Livret A;SAVINGS;Fortuneo;Livret A;LIVRETA;5000;1.00\r\n";

        assertThat(reader.read(csv).rows())
                .singleElement()
                .satisfies(row -> assertThat(row.accountName()).isEqualTo("Fortuneo, Livret A"));
    }
}
