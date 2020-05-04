package github.com.ioridazo.fundanalyzer.domain.dao.master;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProfitAndLessStatementSubjectDao {

    private final JdbcTemplate jdbc;

    public ProfitAndLessStatementSubjectDao(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}