package com.example.impl.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionCallback<T> {
    T doInConnection(Connection connection) throws Exception;
}
