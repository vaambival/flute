package ru.curs.flute.xml2spreadsheet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.After;
import org.junit.Test;

import ru.curs.flute.xml2spreadsheet.XMLDataReader.DescriptorElement;
import ru.curs.flute.xml2spreadsheet.XMLDataReader.DescriptorIteration;
import ru.curs.flute.xml2spreadsheet.XMLDataReader.DescriptorOutput;

public class TestReader {
	private InputStream descrStream;
	private InputStream dataStream;

	@After
	public void TearDown() {
		try {
			descrStream.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			dataStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testParseDescriptor() throws XML2SpreadSheetError, IOException {
		descrStream = TestReader.class
				.getResourceAsStream("testdescriptor.xml");
		dataStream = TestReader.class.getResourceAsStream("testdata.xml");

		XMLDataReader reader = XMLDataReader.createReader(dataStream,
				descrStream, false, null);
		DescriptorElement d = reader.getDescriptor();

		assertEquals(2, d.getSubelements().size());
		assertEquals("report", d.getElementName());
		DescriptorIteration i = (DescriptorIteration) d.getSubelements().get(0);
		assertEquals(0, i.getIndex());
		assertFalse(i.isHorizontal());
		i = (DescriptorIteration) d.getSubelements().get(1);
		assertEquals(-1, i.getIndex());
		assertFalse(i.isHorizontal());

		assertEquals(1, i.getElements().size());
		d = i.getElements().get(0);
		assertEquals("sheet", d.getElementName());
		assertEquals(4, d.getSubelements().size());
		DescriptorOutput o = (DescriptorOutput) d.getSubelements().get(0);
		assertEquals("~{@name}", o.getWorksheet());
		o = (DescriptorOutput) d.getSubelements().get(1);
		assertNull(o.getWorksheet());
		i = (DescriptorIteration) d.getSubelements().get(2);
		assertEquals(-1, i.getIndex());
		assertTrue(i.isHorizontal());

	}

	@Test
	public void testDOMReader1() throws XML2SpreadSheetError {
		descrStream = TestReader.class
				.getResourceAsStream("testdescriptor.xml");
		dataStream = TestReader.class.getResourceAsStream("testdata.xml");

		DummyWriter w = new DummyWriter();
		// Проверяем последовательность генерируемых ридером команд
		XMLDataReader reader = XMLDataReader.createReader(dataStream,
				descrStream, false, w);
		reader.process();
		assertEquals(
				"Q{TCQ{CCQ{CC}C}}Q{TCCQh{CCC}Q{CQh{CCC}CQh{CCC}CQh{CCC}}TCCQh{}Q{}}",
				w.getLog().toString());
	}

	@Test
	public void testDOMReader2() throws XML2SpreadSheetError {
		descrStream = TestReader.class
				.getResourceAsStream("testdescriptor2.xml");
		dataStream = TestReader.class.getResourceAsStream("testdata.xml");

		DummyWriter w = new DummyWriter();
		// Проверяем последовательность генерируемых ридером команд
		XMLDataReader reader = XMLDataReader.createReader(dataStream,
				descrStream, false, w);
		reader.process();
		assertEquals("Q{TCQ{CCQ{CC}C}}Q{TCCQh{CCC}Q{CQh{CCC}}TCCQh{}Q{}}", w
				.getLog().toString());
	}

	@Test
	public void testSAXReader1() throws XML2SpreadSheetError {
		descrStream = TestReader.class
				.getResourceAsStream("testdescriptor.xml");
		dataStream = TestReader.class.getResourceAsStream("testdata.xml");

		DummyWriter w = new DummyWriter();

		XMLDataReader reader = XMLDataReader.createReader(dataStream,
				descrStream, true, w);
		// Проверяем, что на некорректных данных выскакивает корректное
		// сообщение об ошибке
		boolean itHappened = false;
		try {
			reader.process();
		} catch (XML2SpreadSheetError e) {
			itHappened = true;
			assertTrue(e.getMessage().contains(
					"only one iteration element is allowed"));
		}
		assertTrue(itHappened);

	}

	@Test
	public void testSAXReader2() throws XML2SpreadSheetError {
		descrStream = TestReader.class
				.getResourceAsStream("testsaxdescriptor.xml");
		dataStream = TestReader.class.getResourceAsStream("testdata.xml");

		DummyWriter w = new DummyWriter();
		// Проверяем последовательность генерируемых ридером команд
		XMLDataReader reader = XMLDataReader.createReader(dataStream,
				descrStream, false, w);
		reader.process();
		assertEquals("Q{TCQ{CCQ{CC}C}TCQ{CQh{CCC}CQh{CCC}CQh{CCC}}TCQ{}}", w
				.getLog().toString());
	}

	@Test
	public void testSAXReader3() throws XML2SpreadSheetError {
		descrStream = TestReader.class
				.getResourceAsStream("testsaxdescriptor2.xml");
		dataStream = TestReader.class.getResourceAsStream("testdata.xml");

		DummyWriter w = new DummyWriter();
		// Проверяем последовательность генерируемых ридером команд
		XMLDataReader reader = XMLDataReader.createReader(dataStream,
				descrStream, false, w);
		reader.process();
		assertEquals("Q{TCQ{CCQ{CC}C}TCQ{CQh{CCC}}TCQ{}}", w.getLog()
				.toString());
	}
}

class DummyWriter extends ReportWriter {

	private final String[] sheetNames = { "Титульный", "Раздел А", "Раздел Б" };
	private int i;
	private final StringBuilder log = new StringBuilder();

	@Override
	public void sheet(String sheetName, String sourceSheet) {
		// sheeT
		log.append("T");
		assertEquals(sheetNames[i], sheetName);
		i++;
	}

	@Override
	public void startSequence(boolean horizontal) {
		// seQuence
		if (horizontal)
			log.append("Qh{");
		else
			log.append("Q{");
	}

	@Override
	public void endSequence() {
		log.append("}");
	}

	@Override
	public void section(XMLContext context, String sourceSheet,
			RangeAddress range) {
		// seCtion
		log.append("C");
	}

	StringBuilder getLog() {
		return log;
	}

	@Override
	void newSheet(String sheetName, String sourceSheet) {
	}

	@Override
	void putSection(XMLContext context, CellAddress growthPoint2,
			String sourceSheet, RangeAddress range) {
	}

}