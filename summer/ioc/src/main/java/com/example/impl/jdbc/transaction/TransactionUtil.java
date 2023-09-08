package com.example.impl.jdbc.transaction;

import java.sql.Connection;

public class TransactionUtil {
    public static Connection   getCurrentConnection(){
        TransactionStatus ts = DataSourceTransactionManager.transactionStatus.get();
        return ts == null ? null : ts.connection;
    }
}
