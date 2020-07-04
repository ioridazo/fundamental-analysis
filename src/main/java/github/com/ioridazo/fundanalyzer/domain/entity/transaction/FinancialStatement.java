package github.com.ioridazo.fundanalyzer.domain.entity.transaction;

import lombok.Value;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import java.time.LocalDate;
import java.util.Optional;

@SuppressWarnings("RedundantModifiersValueLombok")
@Value
@Entity(immutable = true)
@Table(name = "financial_statement")
public class FinancialStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Integer id;

    private final String companyCode;

    private final String edinetCode;

    private final String financialStatementId;

    private final String subjectId;

    private final LocalDate periodStart;

    private final LocalDate periodEnd;

    private final Long value;

    public Optional<String> getCompanyCode() {
        return Optional.ofNullable(companyCode);
    }

    public Optional<Long> getValue() {
        return Optional.ofNullable(value);
    }
}