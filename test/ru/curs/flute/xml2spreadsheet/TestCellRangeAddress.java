package ru.curs.flute.xml2spreadsheet;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ru.curs.flute.xml2spreadsheet.CellAddress;

public class TestCellRangeAddress {
	@Test
	public void testCellAddress() {
		CellAddress ca = new CellAddress("D12");
		assertEquals(4, ca.getCol());
		assertEquals(12, ca.getRow());
		assertEquals("D12", ca.getAddress());

		ca = new CellAddress("AB11");
		assertEquals(28, ca.getCol());
		assertEquals(11, ca.getRow());
		assertEquals("AB11", ca.getAddress());

		assertEquals(new CellAddress("ZA111"), new CellAddress("ZA111"));
	}

	@Test
	public void testRangeAddress1() throws XML2SpreadSheetError {
		RangeAddress ra = new RangeAddress("D11:G36");
		assertEquals(new CellAddress("D11"), ra.topLeft());
		assertEquals(new CellAddress("G36"), ra.bottomRight());
		assertEquals(4, ra.left());
		assertEquals(7, ra.right());
		assertEquals(11, ra.top());
		assertEquals(36, ra.bottom());
	}

	@Test
	public void testRangeAddress2() throws XML2SpreadSheetError {
		// Автонормализация диапазона
		RangeAddress ra = new RangeAddress("G36:D11");
		assertEquals(new CellAddress("D11"), ra.topLeft());
		assertEquals(new CellAddress("G36"), ra.bottomRight());
		assertEquals(4, ra.left());
		assertEquals(7, ra.right());
		assertEquals(11, ra.top());
		assertEquals(36, ra.bottom());

	}

	@Test
	public void testRangeAddress3() throws XML2SpreadSheetError {
		// Диапазон из одной ячейки
		RangeAddress ra = new RangeAddress("E7");
		assertEquals(new CellAddress("E7"), ra.topLeft());
		assertEquals(new CellAddress("E7"), ra.bottomRight());
		assertEquals(5, ra.left());
		assertEquals(5, ra.right());
		assertEquals(7, ra.top());
		assertEquals(7, ra.bottom());
	}

}
