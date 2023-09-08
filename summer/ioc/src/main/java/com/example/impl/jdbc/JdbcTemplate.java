package com.example.impl.jdbc;

import com.example.impl.jdbc.transaction.TransactionUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcTemplate {
    final DataSource dataSource;
    public JdbcTemplate(DataSource dataSource){
        this.dataSource = dataSource;
    }

    //用户调用
    //why??? 用户传进来一个ConnectionCallBack？？？ 哦 明白了 我们定义接口 用户实现doInConnection!!!
    //这样用户会获得connection 他就可以根据connection操作？？？
    //AutoCommit是管自动提交的 和事务有关
    public <T> T execute(ConnectionCallback<T> action) throws Exception {
        Connection current = TransactionUtil.getCurrentConnection();
        if (current != null) {
            try {
                return action.doInConnection(current);
            } catch (SQLException e) {
                throw new Exception(e);
            }
        }
        try(Connection connection = dataSource.getConnection()){
            boolean autoCommit = connection.getAutoCommit();
            if(!autoCommit) {
                connection.setAutoCommit(true);
            }
            T result = action.doInConnection(connection);
            //这里应该是true 那么下面就不会执行 如果是false为什么又要重新设置为false？？？
            if(!autoCommit){
                connection.setAutoCommit(false);
            }
            return result;

        }catch (SQLException e){
            throw new Exception("数据访问出错");
        }
    }
    //这里有一个问题就是为什么要分两个接口 明明一个接口就能搞定
    //这里要注意 Lambda相当于就是匿名函数 所以被实现的那个方法里面是可以用到外面的参数的
    //其实这个函数 就是要执行下面定义的代码 因为在execute(Conne...... action)方法里面就是一个单纯的do do花阔号里面的逻辑
    public <T> T execute(PreparedStatementCreator psc,PreparedStatementCallback<T> action) throws Exception {
        return execute((Connection conn) ->{
            PreparedStatement preparedStatement = psc.createPreparedStatement(conn);
            return action.doInPreparedStatement(preparedStatement);
        });
    }

    public Number queryForNumber(String sql,Object... args) throws Exception {
        return queryForObject(sql, NumberRowMapper.instance,args);
    }

    public <T> T queryForObject(String sql,Class<T> clazz,Object... args) throws Exception {
        if(clazz == String.class){
            return (T) queryForObject(sql,StringRowMapper.instance,args);
        }
        if(clazz == Boolean.class){
            return (T) queryForObject(sql,BooleanRowMapper.instance,args);
        }
        if(clazz == Number.class){
            return (T) queryForObject(sql, NumberRowMapper.instance,args);
        }
        //bean??
        return (T)queryForObject(sql,new BeanRowMapper<>(clazz),args);

    }
    public <T> T queryForObject(String sql,RowMapper<T> rowMapper,Object... args) throws Exception {
        //rowMapper难道就是把数据库返回的东西 转化成某种java对象？ what is args????
        //两个参数 一个是下面咱们框架自己定义的creator 产生一个PreparedStatement 然后下面这个函数的逻辑是拿到
        //第一个方法产生的PreparedStatement加工 第二个参数我们传进去一个Lambda处理逻辑
        return execute(preparedStatementCreator(sql,args),(PreparedStatement preparedStatement)->{
            //那么这里就是拿到statement里面的逻辑
            T t = null;
            try(ResultSet resultSet = preparedStatement.executeQuery()){
                while (resultSet.next()){
                    if(t == null){
                        t = rowMapper.mapRow(resultSet,resultSet.getRow());
                    }else {
                        throw new SQLException("多行结果？？");
                    }
                }
            }
            //结果为空
            if(Objects.isNull(t)){
                throw new SQLException("queryForObject 执行有问题");
            }
            return t;
        }
        );

    }

    public <T> List<T> queryForList(String sql, Class<T> clazz, Object... args) throws Exception {
        return queryForList(sql,new BeanRowMapper<>(clazz),args);

    }
    public <T> List<T> queryForList(String sql, RowMapper<T> rowMapper, Object... args) throws Exception {
        return execute(preparedStatementCreator(sql,args),(PreparedStatement preparedStatement)->{
            List<T> list = new ArrayList<>();
            try(ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()){
                    list.add(rowMapper.mapRow(resultSet,resultSet.getRow()));
                }
            };
            return list;
        });
    }
    public Number updateAndReturnGeneratedKey(String sql,Object... args) throws Exception {
        return execute((Connection conn)->{
            PreparedStatement preparedStatement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            bindArgs(preparedStatement,args);
            return preparedStatement;
        },(PreparedStatement preparedStatement) ->{
            int n = preparedStatement.executeUpdate();
            if (n == 0) {
                throw new Exception("0 rows inserted.");
            }
            if (n > 1) {
                throw new Exception("Multiple rows inserted.");
            }
            try (ResultSet keys = preparedStatement.getGeneratedKeys()) {
                while (keys.next()) {
                    return (Number) keys.getObject(1);
                }
            }
            throw new Exception("Should not reach here.");
        });
    }
    public int update(String sql,Object... args) throws Exception {
        return execute(preparedStatementCreator(sql,args),(PreparedStatement preparedStatement) ->{
            return preparedStatement.executeUpdate();
        });

    }
    private PreparedStatementCreator preparedStatementCreator(String sql,Object... args){
        //自己定义一个PreparedStatementCreator
        return (Connection conn)->{
            //根据sql生成
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            bindArgs(preparedStatement,args);
            return preparedStatement;
        };
    }
    private void bindArgs(PreparedStatement preparedStatement,Object... args) throws SQLException {
        // PreparedStatement pstmt = con.prepareStatement("UPDATE EMPLOYEES SET SALARY = ? WHERE ID = ?");
        //放到问号里面
        for (int i = 0; i < args.length; i++) {
            preparedStatement.setObject(i + 1, args[i]);
        }
    }


}
class StringRowMapper implements RowMapper<String> {
    static StringRowMapper instance = new StringRowMapper();
    @Override
    public String mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getString(1);
    }
}
class BooleanRowMapper implements RowMapper<Boolean> {

    static BooleanRowMapper instance = new BooleanRowMapper();

    @Override
    public Boolean mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getBoolean(1);
    }
}

class NumberRowMapper implements RowMapper<Number> {

    static NumberRowMapper instance = new NumberRowMapper();

    @Override
    public Number mapRow(ResultSet rs, int rowNum) throws SQLException {
        return (Number) rs.getObject(1);
    }
}