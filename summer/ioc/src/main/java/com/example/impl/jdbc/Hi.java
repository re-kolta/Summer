package com.example.impl.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

public class Hi implements ConnectionCallback{
    @Override
    public Object doInConnection(Connection connection) throws SQLException {
        return null;
    }
}
