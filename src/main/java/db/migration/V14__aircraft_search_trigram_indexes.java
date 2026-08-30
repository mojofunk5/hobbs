package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Adds real substring-search acceleration for {@code GET /aircraft?search=}, revisiting
 * docs/DECISIONS.md's 2026-08-30 "Aircraft picker" entry - that entry deliberately deferred this,
 * since a plain btree index (already added in V7) doesn't speed up an arbitrary-substring
 * {@code LIKE '%x%'} match, and there was no production-scale data yet to confirm the substring
 * scan was actually slow. It's since been observed to be slow against the real ~600k-row imported
 * table (see docs/DECISIONS.md's 2026-08-30 "Aircraft/pilot/airfield picker search performance"
 * entry), so this is that revisit.
 *
 * <p>A Java migration, not SQL, because {@code pg_trgm}/GIN indexes are Postgres-specific -
 * H2 (used by the test suite in PostgreSQL-compatibility mode, per AbstractIntegrationTest) has no
 * equivalent, and substring-search performance only matters at Postgres's real production scale
 * anyway, not against a test suite's tiny fixture tables. This migration is a no-op everywhere
 * except real Postgres, rather than something a plain {@code .sql} migration file could express.
 *
 * <p>The indexes are on {@code lower(column)}, not the raw column, because
 * {@link com.bonney.hobbs.domain.AircraftRepository#search}/{@code #searchByRegistration} query
 * with {@code lower(column) LIKE lower(pattern)} for case-insensitive matching - a trigram index
 * only gets used by the planner when it's built on the exact expression the query filters on, so
 * indexing the raw column here would silently never be used.
 */
public class V14__aircraft_search_trigram_indexes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String productName = context.getConnection().getMetaData().getDatabaseProductName();
        if (!"PostgreSQL".equals(productName)) {
            return;
        }
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            statement.execute(
                    "CREATE INDEX aircraft_registration_trgm_idx ON aircraft USING gin (lower(registration) gin_trgm_ops)");
            statement.execute(
                    "CREATE INDEX aircraft_make_trgm_idx ON aircraft USING gin (lower(make) gin_trgm_ops)");
            statement.execute(
                    "CREATE INDEX aircraft_model_trgm_idx ON aircraft USING gin (lower(model) gin_trgm_ops)");
        }
    }
}
