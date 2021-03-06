package ru.curs.flute;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

import ru.curs.flute.conf.CommonParameters;
import ru.curs.flute.exception.EFluteCritical;

public class CommonParametersTest {

	private static ApplicationContext ctx;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ctx = new AnnotationConfigApplicationContext(TestConfFileLocator.class, CommonParameters.class);
	}

	@Test
	public void testCommonParameters() {
		CommonParameters c = ctx.getBean(CommonParameters.class);
		assertTrue(c.getConnString().startsWith("jdbc:postgresql://127.0.0.1"));
		assertFalse(c.isNeverStop());
		assertEquals(50000, c.getRetryWait());
		assertEquals("postgres", c.getDBUser());
		assertEquals("123", c.getDBPassword());

		assertEquals(12345, (int) c.getRestPort().get());
		assertEquals("127.0.0.1", c.getRestHost());
		assertEquals(360, c.getRestTimeout());
		Properties p = c.getSetupProperties();
		assertEquals("bar", p.getProperty("foo"));
		assertEquals("bar2", p.getProperty("foo2"));
	}

}

class TestConfFileLocator {
	@Bean("confSource")
	static InputStream getConfInputStream() throws EFluteCritical {
		return TestConfFileLocator.class.getResourceAsStream("testsetup.xml");
	}
}