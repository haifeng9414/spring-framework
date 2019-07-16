JDBC（Java Data Base Connectivity）是一套用于SQL语句执行的Java API，原生JDBC使用起来很麻烦，Spring对JDBC做了一层封装，方便使用，核心类就是[JdbcTemplate]，例子：
```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
http://www.springframework.org/schema/beans/spring-beans-2.5.xsd">

    <bean id="bookDao" class="com.dhf.jdbc.example.book.dao.impl.JdbcTemplateBookDao">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <bean id="dataSource" class="org.springframework.jdbc.datasource.DriverManagerDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://10.211.55.4:3306/demo"/>
        <property name="username" value="root"/>
        <property name="password" value="Calong@2015"/>
    </bean>

</beans>
```

```java
public class JdbcTemplateBookDao extends JdbcDaoSupport implements BookDao {
    @Override
    public void insert(Book book) {
        String sql = "INSERT INTO BOOK(BOOK_ID, NAME, YEAR) VALUES (?, ?, ?)";

        getJdbcTemplate().update(sql, new Object[]{book.getBookId(), book.getName(), book.getYear()});
    }

    @Override
    public void insertBatch(final List<Book> books) {
        String sql = "INSERT INTO BOOK (BOOK_ID, NAME, YEAR) VALUES (?, ?, ?)";

        getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {

            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Book customer = books.get(i);
                ps.setLong(1, customer.getBookId());
                ps.setString(2, customer.getName());
                ps.setInt(3, customer.getYear());
            }

            public int getBatchSize() {
                return books.size();
            }
        });
    }

    @Override
    public void delete(Book book) {
        String sql = "DELETE FROM BOOK WHERE BOOK_ID=?";
        getJdbcTemplate().update(sql, new Object[]{book.getBookId()});
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM BOOK";
        getJdbcTemplate().update(sql);
    }

    @Override
    public Book getById(Integer id) {
        String sql = "SELECT * FROM BOOK WHERE BOOK_ID=?";
        return (Book) getJdbcTemplate().queryForObject(sql, new Object[]{id}, new BookRowMapper());
    }

    @Override
    public List<Book> getAll() {
        String sql = "SELECT * FROM BOOK";
        return getJdbcTemplate().query(sql, new BookRowMapper());
    }
}

public static void main(String[] args) {
    ApplicationContext context = new ClassPathXmlApplicationContext("application.xml");

    BookDao bookDao = (BookDao) context.getBean("bookDao");
    bookDao.deleteAll();
    Book book1 = new Book(1, "aaa", 111);
    Book book2 = new Book(2, "bbb", 222);
    Book book3 = new Book(3, "ccc", 333);

    List<Book> books = new ArrayList<>();
    books.add(book1);
    books.add(book2);
    books.add(book3);

    bookDao.insertBatch(books);

    bookDao.delete(book1);

    System.out.println("Book with id =3 : " + bookDao.getById(3));

    for (Book book : bookDao.getAll()) {
        System.out.println(book.toString());
    }
}

/*
输出：
Book with id =3 : Book{bookId=3, name='ccc', year=333}
Book{bookId=2, name='bbb', year=222}
Book{bookId=3, name='ccc', year=333}
*/
```

从上面的例子可以看出，Spring JDBC的核心类是`getJdbcTemplate()`方法返回的[JdbcTemplate]类，[JdbcTemplate]的继承结构很简单：
[JdbcTemplate继承结构](../img/JdbcTemplate.png)

看过笔记[从容器获取Bean](从容器获取Bean.md)就能知道[InitializingBean]的作用了，[JdbcAccessor]抽象类的作用是定义了JDBC操作必备的[DataSource]属性，在`afterPropertiesSet()`方法中验证[DataSource]属性不为空，同时还定义了[SQLExceptionTranslator]属性，用于异常转换，代码：
```java
public abstract class JdbcAccessor implements InitializingBean {
	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private DataSource dataSource;

	@Nullable
	// 将SQL Exception转换为Spring的DataAccessException
	private volatile SQLExceptionTranslator exceptionTranslator;

	// 是否延迟初始化exceptionTranslator
	private boolean lazyInit = true;

	public void setDataSource(@Nullable DataSource dataSource) {
		this.dataSource = dataSource;
	}
  
	@Nullable
	public DataSource getDataSource() {
		return this.dataSource;
	}

	protected DataSource obtainDataSource() {
		DataSource dataSource = getDataSource();
		Assert.state(dataSource != null, "No DataSource set");
		return dataSource;
	}
  
	public void setDatabaseProductName(String dbName) {
		this.exceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(dbName);
	}
  
	public void setExceptionTranslator(SQLExceptionTranslator exceptionTranslator) {
		this.exceptionTranslator = exceptionTranslator;
	}
  
	public SQLExceptionTranslator getExceptionTranslator() {
		SQLExceptionTranslator exceptionTranslator = this.exceptionTranslator;
		if (exceptionTranslator != null) {
			return exceptionTranslator;
		}
		synchronized (this) {
			exceptionTranslator = this.exceptionTranslator;
			if (exceptionTranslator == null) {
				DataSource dataSource = getDataSource();
				if (dataSource != null) {
					exceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
				}
				else {
					exceptionTranslator = new SQLStateSQLExceptionTranslator();
				}
				this.exceptionTranslator = exceptionTranslator;
			}
			return exceptionTranslator;
		}
	}
  
	public void setLazyInit(boolean lazyInit) {
		this.lazyInit = lazyInit;
	}
  
	public boolean isLazyInit() {
		return this.lazyInit;
	}
  
	@Override
	public void afterPropertiesSet() {
		// 验证dataSource属性已被设置，如果lazyInit为false则初始化exceptionTranslator
		if (getDataSource() == null) {
			throw new IllegalArgumentException("Property 'dataSource' is required");
		}
		if (!isLazyInit()) {
			getExceptionTranslator();
		}
	}

}
```

[JdbcOperations]接口定义了JDBC的基本操作集，代码：
```java
public interface JdbcOperations {
	@Nullable
  // 对Connection对象进行操作
	<T> T execute(ConnectionCallback<T> action) throws DataAccessException;

  // static SQL操作
	@Nullable
	<T> T execute(StatementCallback<T> action) throws DataAccessException;

	void execute(String sql) throws DataAccessException;

	@Nullable
	<T> T query(String sql, ResultSetExtractor<T> rse) throws DataAccessException;

	void query(String sql, RowCallbackHandler rch) throws DataAccessException;

	<T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, RowMapper<T> rowMapper) throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, Class<T> requiredType) throws DataAccessException;

	Map<String, Object> queryForMap(String sql) throws DataAccessException;

	<T> List<T> queryForList(String sql, Class<T> elementType) throws DataAccessException;
	List<Map<String, Object>> queryForList(String sql) throws DataAccessException;

	SqlRowSet queryForRowSet(String sql) throws DataAccessException;

	int update(String sql) throws DataAccessException;

	int[] batchUpdate(String... sql) throws DataAccessException;

  // PreparedStatement操作
	@Nullable
	<T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action) throws DataAccessException;

	@Nullable
	<T> T execute(String sql, PreparedStatementCallback<T> action) throws DataAccessException;

// 查询操作相关方法
	@Nullable
	<T> T query(PreparedStatementCreator psc, ResultSetExtractor<T> rse) throws DataAccessException;

	@Nullable
	<T> T query(String sql, @Nullable PreparedStatementSetter pss, ResultSetExtractor<T> rse) throws DataAccessException;

	@Nullable
	<T> T query(String sql, Object[] args, int[] argTypes, ResultSetExtractor<T> rse) throws DataAccessException;

	@Nullable
	<T> T query(String sql, Object[] args, ResultSetExtractor<T> rse) throws DataAccessException;

	@Nullable
	<T> T query(String sql, ResultSetExtractor<T> rse, @Nullable Object... args) throws DataAccessException;

	void query(PreparedStatementCreator psc, RowCallbackHandler rch) throws DataAccessException;

	void query(String sql, @Nullable PreparedStatementSetter pss, RowCallbackHandler rch) throws DataAccessException;

	void query(String sql, Object[] args, int[] argTypes, RowCallbackHandler rch) throws DataAccessException;

	void query(String sql, Object[] args, RowCallbackHandler rch) throws DataAccessException;

	void query(String sql, RowCallbackHandler rch, @Nullable Object... args) throws DataAccessException;

	<T> List<T> query(PreparedStatementCreator psc, RowMapper<T> rowMapper) throws DataAccessException;

	<T> List<T> query(String sql, @Nullable PreparedStatementSetter pss, RowMapper<T> rowMapper) throws DataAccessException;

	<T> List<T> query(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) throws DataAccessException;

	<T> List<T> query(String sql, Object[] args, RowMapper<T> rowMapper) throws DataAccessException;

	<T> List<T> query(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper)
			throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, Object[] args, RowMapper<T> rowMapper) throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, Object[] args, int[] argTypes, Class<T> requiredType)
			throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, Object[] args, Class<T> requiredType) throws DataAccessException;

	@Nullable
	<T> T queryForObject(String sql, Class<T> requiredType, @Nullable Object... args) throws DataAccessException;

	Map<String, Object> queryForMap(String sql, Object[] args, int[] argTypes) throws DataAccessException;

	Map<String, Object> queryForMap(String sql, @Nullable Object... args) throws DataAccessException;

	<T>List<T> queryForList(String sql, Object[] args, int[] argTypes, Class<T> elementType)
			throws DataAccessException;

	<T> List<T> queryForList(String sql, Object[] args, Class<T> elementType) throws DataAccessException;

	<T> List<T> queryForList(String sql, Class<T> elementType, @Nullable Object... args) throws DataAccessException;

	List<Map<String, Object>> queryForList(String sql, Object[] args, int[] argTypes) throws DataAccessException;

	List<Map<String, Object>> queryForList(String sql, @Nullable Object... args) throws DataAccessException;

	SqlRowSet queryForRowSet(String sql, Object[] args, int[] argTypes) throws DataAccessException;

	SqlRowSet queryForRowSet(String sql, @Nullable Object... args) throws DataAccessException;

  // 更新操作相关方法
	int update(PreparedStatementCreator psc) throws DataAccessException;

	int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) throws DataAccessException;

	int update(String sql, @Nullable PreparedStatementSetter pss) throws DataAccessException;

	int update(String sql, Object[] args, int[] argTypes) throws DataAccessException;

	int update(String sql, @Nullable Object... args) throws DataAccessException;

  // 批量更新方法
	int[] batchUpdate(String sql, BatchPreparedStatementSetter pss) throws DataAccessException;

	int[] batchUpdate(String sql, List<Object[]> batchArgs) throws DataAccessException;

	int[] batchUpdate(String sql, List<Object[]> batchArgs, int[] argTypes) throws DataAccessException;

	<T> int[][] batchUpdate(String sql, Collection<T> batchArgs, int batchSize,
			ParameterizedPreparedStatementSetter<T> pss) throws DataAccessException;

  // 存储过程相关方法
	@Nullable
	<T> T execute(CallableStatementCreator csc, CallableStatementCallback<T> action) throws DataAccessException;

	@Nullable
	<T> T execute(String callString, CallableStatementCallback<T> action) throws DataAccessException;

	Map<String, Object> call(CallableStatementCreator csc, List<SqlParameter> declaredParameters)
			throws DataAccessException;

}
```

最后是[JdbcTemplate]类，实现了[JdbcOperations]接口，[JdbcTemplate]类的源码很长，但是有很多功能类似的方法，所以这里不贴源码，直接从例子入手了解[JdbcTemplate]的实现原理，以上面的demo的插入方法为例，代码：
```java
public void insert(Book book) {
    String sql = "INSERT INTO BOOK(BOOK_ID, NAME, YEAR) VALUES (?, ?, ?)";

    getJdbcTemplate().update(sql, new Object[]{book.getBookId(), book.getName(), book.getYear()});
}
```

[JdbcTemplate]中所有对数据库的修改操作，即增删改，都视为update操作，上面调用的`update()`方法代码：
```java
public int update(String sql, @Nullable Object... args) throws DataAccessException {
  return update(sql, newArgPreparedStatementSetter(args));
}

protected PreparedStatementSetter newArgPreparedStatementSetter(@Nullable Object[] args) {
  return new ArgumentPreparedStatementSetter(args);
}

public int update(String sql, @Nullable PreparedStatementSetter pss) throws DataAccessException {
  return update(new SimplePreparedStatementCreator(sql), pss);
}

protected int update(final PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss)
    throws DataAccessException {

  logger.debug("Executing prepared SQL update");

  return updateCount(execute(psc, ps -> {
    try {
      if (pss != null) {
        pss.setValues(ps);
      }
      int rows = ps.executeUpdate();
      if (logger.isDebugEnabled()) {
        logger.debug("SQL update affected " + rows + " rows");
      }
      return rows;
    }
    finally {
      if (pss instanceof ParameterDisposer) {
        ((ParameterDisposer) pss).cleanupParameters();
      }
    }
  }));
}
```

`update(String sql, @Nullable Object... args)`方法中调用的`newArgPreparedStatementSetter`方法返回一个[ArgumentPreparedStatementSetter]对象，能够设置[PreparedStatement]对象的参数，代码：
```java
public class ArgumentPreparedStatementSetter implements PreparedStatementSetter, ParameterDisposer {

	@Nullable
	private final Object[] args;

	public ArgumentPreparedStatementSetter(@Nullable Object[] args) {
		this.args = args;
	}


	@Override
	public void setValues(PreparedStatement ps) throws SQLException {
		if (this.args != null) {
			for (int i = 0; i < this.args.length; i++) {
				Object arg = this.args[i];
				doSetValue(ps, i + 1, arg);
			}
		}
	}

	protected void doSetValue(PreparedStatement ps, int parameterPosition, Object argValue) throws SQLException {
		if (argValue instanceof SqlParameterValue) {
			SqlParameterValue paramValue = (SqlParameterValue) argValue;
			StatementCreatorUtils.setParameterValue(ps, parameterPosition, paramValue, paramValue.getValue());
		}
		else {
      // 根据参数类型设置不同的调用PreparedStatement对象不同的set方法以设置参数
			StatementCreatorUtils.setParameterValue(ps, parameterPosition, SqlTypeValue.TYPE_UNKNOWN, argValue);
		}
	}

	@Override
	public void cleanupParameters() {
    // 如果参数是DisposableSqlTypeValue或SqlValue类型的，则调用其cleanup方法
		StatementCreatorUtils.cleanupParameters(this.args);
	}
}
```

`update(String sql, @Nullable PreparedStatementSetter pss)`方法中创建的[SimplePreparedStatementCreator]类是对SQL字符串的简单封装，能够从执行[Connection]对象中创建[PreparedStatement]对象，代码：
```java
private static class SimplePreparedStatementCreator implements PreparedStatementCreator, SqlProvider {
  private final String sql;

  public SimplePreparedStatementCreator(String sql) {
    Assert.notNull(sql, "SQL must not be null");
    this.sql = sql;
  }

  @Override
  public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
    return con.prepareStatement(this.sql);
  }

  @Override
  public String getSql() {
    return this.sql;
  }
}
```

最后是`update(final PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss)`方法，代码：
```java
protected int update(final PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss)
    throws DataAccessException {

  logger.debug("Executing prepared SQL update");

  return updateCount(execute(psc, ps -> {
    try {
      if (pss != null) {
        // 先用PreparedStatementCreator设置PreparedStatement的参数值
        pss.setValues(ps);
      }
      // 执行更新操作
      int rows = ps.executeUpdate();
      if (logger.isDebugEnabled()) {
        logger.debug("SQL update affected " + rows + " rows");
      }
      // 返回更新的数据行
      return rows;
    }
    finally {
      if (pss instanceof ParameterDisposer) {
        ((ParameterDisposer) pss).cleanupParameters();
      }
    }
  }));
}
```

上面代码的核心在于`execute()`方法，代码：
```java
@Override
@Nullable
public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action)
    throws DataAccessException {

  Assert.notNull(psc, "PreparedStatementCreator must not be null");
  Assert.notNull(action, "Callback object must not be null");
  if (logger.isDebugEnabled()) {
    String sql = getSql(psc);
    logger.debug("Executing prepared SQL statement" + (sql != null ? " [" + sql + "]" : ""));
  }

  // 从DataSource获取Connection
  Connection con = DataSourceUtils.getConnection(obtainDataSource());
  PreparedStatement ps = null;
  try {
    ps = psc.createPreparedStatement(con);
    // 设置jdbcTemplate的fetchSize、maxRows和queryTimeout属性到PreparedStatement
    applyStatementSettings(ps);
    // 调用PreparedStatementCallback执行SQL操作
    T result = action.doInPreparedStatement(ps);
    // 如果PreparedStatement存在warning并且ignoreWarnings为false，则封装为SQLWarningException抛出
    handleWarnings(ps);
    return result;
  }
  catch (SQLException ex) {
    // Release Connection early, to avoid potential connection pool deadlock
    // in the case when the exception translator hasn't been initialized yet.
    if (psc instanceof ParameterDisposer) {
      ((ParameterDisposer) psc).cleanupParameters();
    }
    String sql = getSql(psc);
    JdbcUtils.closeStatement(ps);
    // 设置ps == null使得finally的closeStatement不再对ps做处理
    ps = null;
    // 发生异常时释放连接
    DataSourceUtils.releaseConnection(con, getDataSource());
    // 设置con == null使得finally的releaseConnection不再对con做处理
    con = null;
    throw translateException("PreparedStatementCallback", sql, ex);
  }
  finally {
    // 下面的方法在catch中已经调用过了，但是重复调用下面的几个方法没有影响
    if (psc instanceof ParameterDisposer) {
      ((ParameterDisposer) psc).cleanupParameters();
    }
    // close PreparedStatement
    JdbcUtils.closeStatement(ps);
    DataSourceUtils.releaseConnection(con, getDataSource());
  }
}
```

`execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action)`方法定义了SQL执行过程的基本逻辑，包括获取连接、创建[PreparedStatement]和释放连接，SQL执行过程中的差异行为则交由[PreparedStatementCallback]处理，回到`update(final PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss)`方法，该方法传入的[PreparedStatementCallback]为：
```java
ps -> {
  try {
    if (pss != null) {
      // 先用PreparedStatementCreator设置PreparedStatement的参数值
      pss.setValues(ps);
    }
    // 执行更新操作
    int rows = ps.executeUpdate();
    if (logger.isDebugEnabled()) {
      logger.debug("SQL update affected " + rows + " rows");
    }
    // 返回更新的数据行
    return rows;
  }
  finally {
    if (pss instanceof ParameterDisposer) {
      ((ParameterDisposer) pss).cleanupParameters();
    }
  }
}
```

对于其他的update操作，包括增删改都是上面的执行过程，执行的是同一个`update()`方法，对于批量操作，和上面的流程有些许不同，批量操作方法的使用例子：
```java
public void insertBatch(final List<Book> books) {
    String sql = "INSERT INTO BOOK (BOOK_ID, NAME, YEAR) VALUES (?, ?, ?)";

    getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {

        public void setValues(PreparedStatement ps, int i) throws SQLException {
            Book customer = books.get(i);
            ps.setLong(1, customer.getBookId());
            ps.setString(2, customer.getName());
            ps.setInt(3, customer.getYear());
        }

        public int getBatchSize() {
            return books.size();
        }
    });
}
```

批量操作执行时需要传入一个[BatchPreparedStatementSetter]实例，用于设置批量操作时的参数，下面是`batchUpdate(String sql, final BatchPreparedStatementSetter pss)`方法代码：

```java
@Override
public int[] batchUpdate(String sql, final BatchPreparedStatementSetter pss) throws DataAccessException {
  if (logger.isDebugEnabled()) {
    logger.debug("Executing SQL batch update [" + sql + "]");
  }

  int[] result = execute(sql, (PreparedStatementCallback<int[]>) ps -> {
    try {
      int batchSize = pss.getBatchSize();
      InterruptibleBatchPreparedStatementSetter ipss =
          (pss instanceof InterruptibleBatchPreparedStatementSetter ?
          (InterruptibleBatchPreparedStatementSetter) pss : null);
      // 首先判断数据库是否支持批量操作
      if (JdbcUtils.supportsBatchUpdates(ps.getConnection())) {
        for (int i = 0; i < batchSize; i++) {
          pss.setValues(ps, i);
          // 如果ipss不为空，则判断isBatchExhausted是否为true，该方法可以在batchSize的基础上进一步控制批量操作的操作数量
          if (ipss != null && ipss.isBatchExhausted(i)) {
            break;
          }
          ps.addBatch();
        }
        return ps.executeBatch();
      }
      else {
        // 如果不支持批量操作，则循环依次执行
        List<Integer> rowsAffected = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
          pss.setValues(ps, i);
          if (ipss != null && ipss.isBatchExhausted(i)) {
            break;
          }
          // 保存执行结果
          rowsAffected.add(ps.executeUpdate());
        }
        int[] rowsAffectedArray = new int[rowsAffected.size()];
        for (int i = 0; i < rowsAffectedArray.length; i++) {
          rowsAffectedArray[i] = rowsAffected.get(i);
        }
        return rowsAffectedArray;
      }
    }
    finally {
      if (pss instanceof ParameterDisposer) {
        ((ParameterDisposer) pss).cleanupParameters();
      }
    }
  });

  Assert.state(result != null, "No result array");
  return result;
}
```

`batchUpdate(String sql, final BatchPreparedStatementSetter pss)`方法中调用的`execute()`方法和普通的更新操作调用的`execute()`方法是同一个。

以上是更新操作的执行过程，下面看查询过程，一个普通的查询操作如下：
```java
public List<Book> getAll() {
    String sql = "SELECT * FROM BOOK";
    return getJdbcTemplate().query(sql, new BookRowMapper());
}
```

传入的第二个参数[BookRowMapper]是[RowMapper]接口的实现类，[RowMapper]接口用于结果集的转换，[BookRowMapper]实现如下：
```java
public class BookRowMapper implements RowMapper {
    // rowNum表示当前行数
    public Book mapRow(ResultSet rs, int rowNum) throws SQLException {
        Book book = new Book();
        book.setBookId(rs.getInt("BOOK_ID"));
        book.setName(rs.getString("NAME"));
        book.setYear(rs.getInt("YEAR"));
        return book;
    }
}
```

上面调用的`query()`方法代码：
```java
public <T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException {
  return result(query(sql, new RowMapperResultSetExtractor<>(rowMapper)));
}
```

[RowMapperResultSetExtractor]类用于遍历[ResultSet]转换结果集，代码：
```java
public class RowMapperResultSetExtractor<T> implements ResultSetExtractor<List<T>> {

	private final RowMapper<T> rowMapper;

	private final int rowsExpected;

	public RowMapperResultSetExtractor(RowMapper<T> rowMapper) {
		this(rowMapper, 0);
	}

	public RowMapperResultSetExtractor(RowMapper<T> rowMapper, int rowsExpected) {
		Assert.notNull(rowMapper, "RowMapper is required");
		this.rowMapper = rowMapper;
		this.rowsExpected = rowsExpected;
	}


	@Override
	public List<T> extractData(ResultSet rs) throws SQLException {
		List<T> results = (this.rowsExpected > 0 ? new ArrayList<>(this.rowsExpected) : new ArrayList<>());
		int rowNum = 0;
		while (rs.next()) {
			results.add(this.rowMapper.mapRow(rs, rowNum++));
		}
		return results;
	}
}
```

`query(final String sql, final ResultSetExtractor<T> rse)`方法代码：
```java
public <T> T query(final String sql, final ResultSetExtractor<T> rse) throws DataAccessException {
  Assert.notNull(sql, "SQL must not be null");
  Assert.notNull(rse, "ResultSetExtractor must not be null");
  if (logger.isDebugEnabled()) {
    logger.debug("Executing SQL query [" + sql + "]");
  }

  class QueryStatementCallback implements StatementCallback<T>, SqlProvider {
    @Override
    @Nullable
    public T doInStatement(Statement stmt) throws SQLException {
      ResultSet rs = null;
      try {
        rs = stmt.executeQuery(sql);
        // 调用ResultSetExtractor转换结果集
        return rse.extractData(rs);
      }
      finally {
        JdbcUtils.closeResultSet(rs);
      }
    }
    @Override
    public String getSql() {
      return sql;
    }
  }

  return execute(new QueryStatementCallback());
}
```

`execute(StatementCallback<T> action)`方法代码：
```java
public <T> T execute(StatementCallback<T> action) throws DataAccessException {
  Assert.notNull(action, "Callback object must not be null");

  Connection con = DataSourceUtils.getConnection(obtainDataSource());
  Statement stmt = null;
  try {
    stmt = con.createStatement();
    applyStatementSettings(stmt);
    T result = action.doInStatement(stmt);
    handleWarnings(stmt);
    return result;
  }
  catch (SQLException ex) {
    // Release Connection early, to avoid potential connection pool deadlock
    // in the case when the exception translator hasn't been initialized yet.
    String sql = getSql(action);
    JdbcUtils.closeStatement(stmt);
    stmt = null;
    DataSourceUtils.releaseConnection(con, getDataSource());
    con = null;
    throw translateException("StatementCallback", sql, ex);
  }
  finally {
    JdbcUtils.closeStatement(stmt);
    DataSourceUtils.releaseConnection(con, getDataSource());
  }
}
```

可以看到查询过程和更新过程区别不大，其他的查询方式也差不多是上面的执行过程

[JdbcTemplate]: aaa
[InitializingBean]: aaa
[JdbcAccessor]: aaa
[SQLExceptionTranslator]: aaa
[DataSource]: aaa
[JdbcOperations]: aaa
[Connection]: aaa
[PreparedStatement]: aaa
[PreparedStatementCallback]: aaa
[RowMapperResultSetExtractor]: aaa
[ResultSet]: aaa