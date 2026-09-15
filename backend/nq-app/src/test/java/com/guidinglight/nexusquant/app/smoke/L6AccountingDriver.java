package com.guidinglight.nexusquant.app.smoke;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** 只由L6子进程显式选用，L5及生产driver保持原样。 */
public final class L6AccountingDriver extends org.postgresql.Driver {
    @Override public Connection connect(String url, Properties properties) throws SQLException {
        Connection connection = super.connect(url, properties);
        if (connection != null) L6TransactionAccounting.connectionEstablished();
        return connection == null ? null : L6TransactionAccounting.wrap(connection, "APP_UNCLASSIFIED");
    }
}
