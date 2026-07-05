/*
 * The MIT License (MIT)
 *
 * Copyright © 2020 - 2026 - OpenLogin Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.nickuc.openlogin.common.database;

import com.mysql.cj.jdbc.Driver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MySQL implements Database {

    private final HikariConfig hikariConfig;
    private HikariDataSource dataSource;

    public MySQL(@NonNull String host, int port, @NonNull String database, @NonNull String username,
                 String password, boolean useSSL, int poolSize) {
        hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSSL + "&autoReconnect=true&characterEncoding=utf8");
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password == null ? "" : password);
        // referencing the class directly (instead of a string) keeps this working after relocation/shading
        hikariConfig.setDriverClassName(Driver.class.getName());
        hikariConfig.setPoolName("OpeNLogin-MySQL");
        hikariConfig.setMaximumPoolSize(Math.max(1, poolSize));
    }

    /**
     * Open the connection pool.
     *
     * @throws SQLException on failure
     */
    public void openConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            try {
                dataSource = new HikariDataSource(hikariConfig);
            } catch (Exception e) {
                throw new SQLException("Failed to open the MySQL connection pool", e);
            }
        }
    }

    /**
     * Close the connection pool.
     */
    public void closeConnection() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Executes an update.
     *
     * @param command the command to be executed
     * @param args    the command arguments
     * @throws SQLException on failure
     */
    public void update(String command, Object... args) throws SQLException {
        openConnection();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(command)) {
            for (int i = 0; i < args.length; i++) {
                preparedStatement.setObject(i + 1, args[i]);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException("Failed to execute update statement: '" + command + "'", e);
        }
    }

    /**
     * Executes a query.
     *
     * @param command the command to be executed
     * @param args    the command arguments
     * @return returns an instance of {@link com.nickuc.openlogin.common.database.Database.Query}
     * @throws SQLException on failure
     */
    public Query query(String command, Object... args) throws SQLException {
        openConnection();
        return new Query(dataSource.getConnection(), true, command, args);
    }
}
