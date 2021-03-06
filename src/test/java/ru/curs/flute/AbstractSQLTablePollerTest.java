package ru.curs.flute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.dbunit.Assertion;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import ru.curs.celesta.vintage.Celesta;
import ru.curs.flute.exception.EFluteNonCritical;
import ru.curs.flute.source.SqlTablePoller;
import ru.curs.flute.task.FluteTask;
import ru.curs.flute.task.FluteTaskState;
import ru.curs.flute.task.QueueTask;
import ru.curs.flute.task.TaskUnit;

public abstract class AbstractSQLTablePollerTest {

	private ApplicationContext ctx;

	protected abstract JDBCConnectionPool.DBType getDBType();

	@Before
	public void setup() {
		TestSQLConf.dbType = getDBType();
		ctx = new AnnotationConfigApplicationContext(TestSQLConf.class);
	}

	@Test
	public void tasksCanBeExtracted() throws Exception {

		IDatabaseConnection conn = initData();

		SqlTablePoller poller = ctx.getBean(TestSQLTablePoller.class);

		poller.setTableName("\"tasks\"");
		poller.setQueryPeriod(500);
		QueueTask t = poller.getTask();
		assertNotNull(t);
		assertEquals(1, t.getId());
		assertEquals("hello", t.getTaskUnit().getQualifier());
		assertEquals(TaskUnit.Type.SCRIPT, t.getTaskUnit().getType());
		assertEquals("param1", t.getParams());

		t = poller.getTask();
		assertEquals(2, t.getId());
		assertEquals("hello", t.getTaskUnit().getQualifier());
		assertEquals(TaskUnit.Type.SCRIPT, t.getTaskUnit().getType());
		assertEquals("param2", t.getParams());

		IDataSet actual = conn.createDataSet();
		IDataSet expected = new XmlDataSet(
				AbstractSQLTablePollerTest.class.getResourceAsStream("expectedTasksDataset.xml"));

		Assertion.assertEquals(expected.getTable("tasks"), actual.getTable("tasks"));

		t = poller.getTask();
		assertEquals(3, t.getId());
		assertEquals("hello", t.getTaskUnit().getQualifier());
		assertEquals(TaskUnit.Type.SCRIPT, t.getTaskUnit().getType());
		assertEquals("param3", t.getParams());

		t = poller.getTask();
		assertEquals(4, t.getId());
		assertEquals("ru.curs.Hello#hello", t.getTaskUnit().getQualifier());
		assertEquals(TaskUnit.Type.PROC, t.getTaskUnit().getType());
		assertEquals("param4", t.getParams());

		CompletableFuture<QueueTask> f = CompletableFuture.supplyAsync(() -> {
			try {
				return poller.getTask();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});
		Thread.sleep(100);
		assertFalse(f.isDone());
		IDataSet dataSet = new XmlDataSet(AbstractSQLTablePollerTest.class.getResourceAsStream("newTaskDataSet.xml"));
		DatabaseOperation.INSERT.execute(conn, dataSet);
		t = f.get();
		assertEquals(5, t.getId());
		assertEquals("foo", t.getTaskUnit().getQualifier());
		assertEquals(TaskUnit.Type.SCRIPT, t.getTaskUnit().getType());
		assertEquals("task5", t.getParams());

		t = poller.getTask();
		assertEquals(6, t.getId());
		assertEquals("ru.curs.Foo#foo", t.getTaskUnit().getQualifier());
		assertEquals(TaskUnit.Type.PROC, t.getTaskUnit().getType());
		assertEquals("task6", t.getParams());

		conn.close();
	}

	@Test
	public void taskStateIsStoredInDB() throws Exception {
		IDatabaseConnection conn = initData();
		SqlTablePoller poller = ctx.getBean(TestSQLTablePoller.class);
		poller.setTableName("\"tasks\"");
		poller.setQueryPeriod(500);

		QueueTask t1 = poller.getTask();
		t1.setMessage("foo");
		t1.setState(FluteTaskState.SUCCESS);

		QueueTask t2 = poller.getTask();
		t2.setState(FluteTaskState.INTERRUPTED);

		QueueTask t3 = poller.getTask();
		t3.setMessage("error message");
		t3.setState(FluteTaskState.FAIL);

		QueueTask t4 = poller.getTask();
		t4.setMessage("foo");
		t4.setState(FluteTaskState.SUCCESS);


		IDataSet actual = conn.createDataSet();
		InputStream is = AbstractSQLTablePollerTest.class.getResourceAsStream("expectedTasksDataset2.xml");
		IDataSet expected = new XmlDataSet(is);

		Assertion.assertEquals(expected.getTable("tasks"), actual.getTable("tasks"));

		conn.close();
	}

	@Test
	@Ignore
	public void tasksAreExecutedFromQueue() throws Exception {
		IDatabaseConnection conn = initData();
		TestSQLTablePoller poller = ctx.getBean(TestSQLTablePoller.class);

		poller.setMaxThreads(2);
		poller.setTableName("\"tasks\"");
		final Set<String> expected = Collections
				.synchronizedSet(new HashSet<>(Arrays.asList("param1", "param2", "param3")));
		ExecutorService es = Executors.newSingleThreadExecutor();
		poller.log = s -> {
			expected.remove(s);
		};
		es.submit(poller);
		while (!expected.isEmpty()) {
			es.awaitTermination(10, TimeUnit.MILLISECONDS);
		}
		es.shutdown();
		assertTrue(expected.isEmpty());
		IDataSet actual = conn.createDataSet();
		InputStream is = AbstractSQLTablePollerTest.class.getResourceAsStream("expectedTasksDataset3.xml");
		IDataSet expectedDs = new XmlDataSet(is);
		Assertion.assertEquals(expectedDs.getTable("tasks"), actual.getTable("tasks"));
		conn.close();
	}

	protected IDatabaseConnection initData() throws DataSetException, DatabaseUnitException, Exception, SQLException {
		IDataSet dataSet = new XmlDataSet(AbstractSQLTablePollerTest.class.getResourceAsStream("tasksDataset.xml"));

		IDatabaseConnection conn = new DatabaseConnection(ctx.getBean(JDBCConnectionPool.class).get());
		conn.getConfig().setProperty("http://www.dbunit.org/properties/escapePattern", "\"?\"");
		conn.getConfig().setProperty("http://www.dbunit.org/features/caseSensitiveTableNames", true);

		DatabaseOperation.CLEAN_INSERT.execute(conn, dataSet);
		return conn;
	}

}

@Component
@Scope("prototype")
class TestSQLTablePoller extends SqlTablePoller {
	Consumer<String> log = s -> {
	};

	@Override
	public void process(FluteTask task) throws InterruptedException, EFluteNonCritical {
		log.accept(task.getParams());
		task.setMessage("!" + task.getParams());
	}

}

@Configuration
@Import({ SqlTablePoller.class, TestSQLTablePoller.class })
class TestSQLConf {

	@Bean
	public GlobalParams getGlobalParams() {
		return new GlobalParams() {
			@Override
			public int getRetryWait() {
				return 0;
			}

			@Override
			public boolean isNeverStop() {
				return true;
			}

			@Override
			public String getFluteUserId() {
				return "flute";
			}

			@Override
			public boolean isExposeRedis() {
				return false;
			}

		};
	}

	static JDBCConnectionPool.DBType dbType;

	@Bean
	public JDBCConnectionPool pool() {
		return new JDBCConnectionPool() {
			private final JdbcConnectionPool cp;

			{
				String mode = dbType.toString();
				cp = JdbcConnectionPool.create(String.format("jdbc:h2:mem:test"), "sa", "sa");

				String sql;
				try (InputStream is = AbstractSQLTablePollerTest.class.getResourceAsStream("taskstable.sql")) {
					try (Scanner s = new Scanner(is)) {
						sql = s.useDelimiter("\\Z").next();
					}
				} catch (IOException e) {
					e.printStackTrace();
					sql = null;
				}

				// System.out.println(sql);

				try (Connection conn = cp.getConnection()) {
					try (Statement stmt = conn.createStatement()) {
						stmt.execute(sql);
						stmt.execute(String.format("set mode %s;", mode));
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}

			}

			@Override
			public Connection get() throws Exception {
				return cp.getConnection();
			}

			@Override
			public void putBack(Connection conn) {
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void commit(Connection conn) {
				try {
					conn.commit();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

			@Override
			public DBType getDBType() {
				return dbType;
			}
		};

	}

	@Bean
	public Celesta getCelesta() {
		return null;
	}

}
