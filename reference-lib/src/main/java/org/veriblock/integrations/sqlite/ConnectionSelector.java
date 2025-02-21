// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.integrations.sqlite;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.JDBC;

public class ConnectionSelector {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionSelector.class);

    public static final String defaultDatabaseName = "database.sqlite";
    public static final String testDatabaseName = "database-test.sqlite";

    private ConnectionSelector() { }

    public static Connection setConnection(String databasePath) throws SQLException
    {
        logger.info("SqlLite path: '{}'", databasePath);
        String url = String.format("jdbc:sqlite:%s", databasePath);

        DriverManager.registerDriver(new JDBC());
        Connection connection = DriverManager.getConnection(url);
        return connection;
    }

    public static Connection setConnectionDefault() throws SQLException
    {
        String databasePath = Paths.get(FileManager.getDataDirectory(), defaultDatabaseName).toString();
        return setConnection(databasePath);
    }

    public static Connection setConnectionTestnet() throws SQLException
    {
        String databasePath = Paths.get(FileManager.getDataDirectory(), testDatabaseName).toString();
        return setConnection(databasePath);
    }
}
