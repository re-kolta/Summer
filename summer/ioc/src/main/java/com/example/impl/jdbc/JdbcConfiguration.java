package com.example.impl.jdbc;

import com.example.impl.annotation.SummerBean;
import com.example.impl.annotation.SummerConfiguration;
import com.example.impl.annotation.SummerValue;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

@SummerConfiguration
public class JdbcConfiguration {
    //注入配置文件


    @SummerBean
    public DataSource geneDataSource(String url , String username , String password,String driverName,
                                     int minimumPoolSize,int maximumPoolSize,int connTimeout){
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName(driverName);
        //???在JdbcTemplate里面为什么又设置为true
        hikariConfig.setAutoCommit(false);

        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setMinimumIdle(minimumPoolSize);
        hikariConfig.setConnectionTimeout(connTimeout);
        return new HikariDataSource(hikariConfig);

    }
}
