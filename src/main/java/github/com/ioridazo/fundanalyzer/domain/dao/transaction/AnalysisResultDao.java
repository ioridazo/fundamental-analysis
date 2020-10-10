package github.com.ioridazo.fundanalyzer.domain.dao.transaction;

import github.com.ioridazo.fundanalyzer.domain.entity.transaction.AnalysisResult;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;
import org.seasar.doma.jdbc.Result;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ConfigAutowireable
@Dao
public interface AnalysisResultDao {

    @Select
    Optional<AnalysisResult> selectByUniqueKey(final String companyCode, final LocalDate period);

    @Select
    List<AnalysisResult> selectByCompanyCode(final String code);

    @Select
    List<AnalysisResult> selectByPeriod(final LocalDate period);

    @Insert
    Result<AnalysisResult> insert(final AnalysisResult analysisResult);
}
