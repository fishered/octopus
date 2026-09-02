package com.accuenergy.octopus.mgmt.infrastructure.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class PostgresTotpCounterStore implements TotpCounterStore {
    private final JdbcTemplate jdbc;

    public PostgresTotpCounterStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public boolean tryAdvance(UUID factorId, long counter) {
        return jdbc.update("""
                update mfa_factor
                   set last_accepted_counter = ?
                 where id = ?
                   and status = 'ACTIVE'
                   and last_accepted_counter < ?
                """, counter, factorId, counter) == 1;
    }
}
