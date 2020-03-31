package github.com.ioridazo.fundamentalanalysis.domain.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectDao {

    private final JdbcTemplate jdbc;

    public SubjectDao(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
