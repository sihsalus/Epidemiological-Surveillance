package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db;

import java.sql.*;
import java.util.*;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/** Checks every retirement dependency before Liquibase executes the first DDL statement. */
public class VerifyUnusedSurveillanceTables implements CustomTaskChange {

	private static final List<String> TABLES = Arrays.asList("epidemiologysurveillance_item", "outbreak_alert",
	    "outbreak_alert_rule", "epidemiological_focus", "individual_record");

	@Override
    public void execute(Database database) throws CustomChangeException {
        try {
            Connection connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
            String catalog = connection.getCatalog();
            String schema = database.getDefaultSchemaName();
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, String> physical = new HashMap<>();
            try (ResultSet tables = metadata.getTables(catalog, schema, "%", new String[] { "TABLE" })) {
                while (tables.next())
                    physical.put(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT), tables.getString("TABLE_NAME"));
            }
            Set<String> expectedKeys = new HashSet<>(Arrays.asList("sc_record_fk", "oa_rule_fk"));
            for (String table : TABLES) {
                String name = physical.get(table);
                if (name == null)
                    throw new CustomChangeException("Retirement halted: missing table " + table);
                requireEmpty(connection, "SELECT COUNT(*) FROM " + database.escapeTableName(catalog, schema, name), table);
                try (ResultSet keys = metadata.getExportedKeys(catalog, schema, name)) {
                    while (keys.next()) {
                        String key = keys.getString("FK_NAME").toLowerCase(Locale.ROOT);
                        String child = keys.getString("FKTABLE_NAME").toLowerCase(Locale.ROOT);
                        String column = keys.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT);
                        String parentColumn = keys.getString("PKCOLUMN_NAME").toLowerCase(Locale.ROOT);
                        boolean sameSchema = Objects.equals(keys.getString("PKTABLE_CAT"), keys.getString("FKTABLE_CAT"))
                            && Objects.equals(keys.getString("PKTABLE_SCHEM"), keys.getString("FKTABLE_SCHEM"));
                        boolean expected = sameSchema && (
                            ("individual_record".equals(table) && "sc_record_fk".equals(key)
                                && "surveillance_case".equals(child) && "individual_record_id".equals(column)
                                && "individual_record_id".equals(parentColumn))
                            || ("outbreak_alert_rule".equals(table) && "oa_rule_fk".equals(key)
                                && "outbreak_alert".equals(child) && "rule_id".equals(column)
                                && "rule_id".equals(parentColumn)));
                        if (!expected || !expectedKeys.remove(key))
                            throw new CustomChangeException("Retirement halted: unexpected foreign key " + key);
                    }
                }
            }
            if (!expectedKeys.isEmpty())
                throw new CustomChangeException("Retirement halted: missing expected foreign keys " + expectedKeys);
            String cases = physical.get("surveillance_case");
            if (cases == null)
                throw new CustomChangeException("Retirement halted: missing surveillance_case");
            requireEmpty(connection, "SELECT COUNT(*) FROM " + database.escapeTableName(catalog, schema, cases)
                + " WHERE individual_record_id IS NOT NULL", "surveillance_case.individual_record_id");
        } catch (SQLException ex) {
            throw new CustomChangeException("Could not verify retired tables; no retirement DDL was executed", ex);
        }
    }

	private void requireEmpty(Connection connection, String query, String subject)
            throws SQLException, CustomChangeException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
            rows.next();
            if (rows.getLong(1) != 0)
                throw new CustomChangeException("Retirement halted: " + subject + " contains data or references");
        }
    }

	@Override
	public String getConfirmationMessage() {
		return "Retired tables are empty and their incoming foreign keys match the reviewed schema";
	}

	@Override
	public void setUp() {
	}

	@Override
	public void setFileOpener(ResourceAccessor accessor) {
	}

	@Override
	public ValidationErrors validate(Database database) {
		return new ValidationErrors();
	}
}
