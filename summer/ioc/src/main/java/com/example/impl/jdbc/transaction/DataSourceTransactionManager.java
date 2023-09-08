package com.example.impl.jdbc.transaction;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class DataSourceTransactionManager implements InvocationHandler,PlatformTransactionManager{
    static final ThreadLocal<TransactionStatus> transactionStatus= new ThreadLocal<>();
    final DataSource dataSource;
    public DataSourceTransactionManager (DataSource dataSource){
        this.dataSource = dataSource;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        TransactionStatus ts = transactionStatus.get();
        if(Objects.isNull(ts)){
            try(Connection connection = dataSource.getConnection()){
                final  boolean autoCommit =   connection.getAutoCommit();
                if(autoCommit){
                    connection.setAutoCommit(false);
                }
                try{
                    transactionStatus.set(new TransactionStatus(connection));
                    Object r = method.invoke(proxy,args);
                    connection.commit();
                    return r;
                }catch (InvocationTargetException e){
                    //事务出现异常
                    connection.rollback();
                    throw e;
                }finally {
                    transactionStatus.remove();
                    if(autoCommit){
                        connection.setAutoCommit(true);
                    }
                }
            }

        }
        else {
            return method.invoke(proxy,args);
        }


    }
}
