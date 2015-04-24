package ru.curs.flute.excel2print;

import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.print.PrintService;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;

import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.poi.hssf.converter.ExcelToFoConverter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Класс-обёртка, осуществляющий конвертацию книги Excel в форматы XSL-FO и PDF
 * и вывод на принтер.
 */
public class Excel2Print {

	private final HSSFWorkbook wb;
	private File fopConfig;
	private FopFactory fopFactory;
	private String printerName;

	public Excel2Print(HSSFWorkbook wb) {
		if (wb == null)
			throw new IllegalArgumentException("Workbook cannot be null!");
		this.wb = wb;
	}

	public Excel2Print(InputStream is) throws InvalidFormatException,
			IOException {
		if (is == null)
			throw new IllegalArgumentException(
					"Workbook input stream cannot be null!");
		wb = (HSSFWorkbook) WorkbookFactory.create(is);
	}

	private FopFactory getFopFactory() throws IOException, SAXException {
		if (fopFactory == null) {
			fopFactory = FopFactory.newInstance();
			fopFactory.setUserConfig(fopConfig);
		}
		return fopFactory;
	}

	/**
	 * Задаёт имя конфигурационного файла FOP.
	 * 
	 * @param config
	 *            Имя конфигурационного файла.
	 * @throws IOException
	 *             Если файла не существует или если он не может быть прочитан.
	 */
	public void setFopConfig(String config) throws IOException {
		if (config != null) {
			File f = new File(config);
			setFopConfig(f);
		} else {
			fopConfig = null;
		}
	}

	/**
	 * Задаёт конфигурационный файл FOP.
	 * 
	 * @param config
	 *            Конфигурационный файл FOP.
	 * @throws IOException
	 *             Если файла не существует или если он не может быть прочитан.
	 */
	public void setFopConfig(File config) throws IOException {
		if (config != null) {
			if (!(config.exists() && config.canRead()))
				throw new IOException(String.format(
						"File '%s' does not exists or cannot be read.", config));
		}
		fopConfig = config;
	}

	/**
	 * Конвертирует книгу в FO-документ.
	 * 
	 * @param doc
	 *            FO-документ.
	 */
	public void toFO(Document doc) {
		ExcelToFoConverter conv = new ExcelToFoConverter(doc);
		conv.setOutputColumnHeaders(false);
		conv.setOutputRowNumbers(false);
		conv.setOutputSheetNames(false);
		conv.processWorkbook(wb);
	}

	/**
	 * Конвертирует книгу в FO-документ и сохраняет её в поток.
	 * 
	 * @param os
	 *            Поток для записи FO-документа.
	 * @throws Exception
	 *             ошибка формата книги, ошибка ввода-вывода, ошибка
	 *             конфигурации системы.
	 */
	public void toFO(OutputStream os) throws Exception {
		Document doc = getDoc();
		TransformerFactory transformerFactory = TransformerFactory
				.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult streamResult = new StreamResult(os);
		transformer.transform(source, streamResult);
	}

	private Document getDoc() throws ParserConfigurationException {
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		Document doc = db.newDocument();
		toFO(doc);
		return doc;
	}

	/**
	 * Конвертирует книгу в PDF.
	 * 
	 * @param out
	 *            Поток для записи PDF-файла.
	 * @throws Exception
	 *             ошибка формата книги, ошибка ввода-вывода, ошибка
	 *             конфигурации системы.
	 */
	public void toPDF(OutputStream out) throws Exception {
		Document doc = getDoc();
		Fop fop = getFopFactory().newFop(MimeConstants.MIME_PDF, out);
		fopTransform(doc, fop);
	}

	/**
	 * Отправляет книгу на печать на принтер.
	 * 
	 * @throws Exception
	 *             ошибка формата книги, ошибка ввода-вывода, ошибка
	 *             конфигурации системы.
	 */
	@SuppressWarnings("unchecked")
	public void toPrinter() throws Exception {
		Document doc = getDoc();

		if (printerName == null) {
			OutputStream out = new ByteArrayOutputStream();
			try {
				Fop fop = getFopFactory().newFop(MimeConstants.MIME_FOP_PRINT,
						out);

				fopTransform(doc, fop);
			} finally {
				out.close();
			}
		} else {
			PrintService[] services = PrinterJob.lookupPrintServices();
			PrintService srv = null;
			StringBuilder printerNames = new StringBuilder();
			for (int i = 0; i < services.length; i++) {
				if (printerName.equals(services[i].getName())) {
					srv = services[i];
				} else {
					if (printerNames.length() > 0)
						printerNames.append("; ");
					printerNames.append(services[i].getName());
				}
			}
			if (srv == null) {
				throw new Exception(
						String.format(
								"No printer service named '%s' found. Availiable services are: %s.",
								printerName, printerNames.toString()));
			}
			PrinterJob printerJob = PrinterJob.getPrinterJob();
			printerJob.setPrintService(srv);
			FOUserAgent userAgent = getFopFactory().newFOUserAgent();
			userAgent.getRendererOptions().put("printerjob", printerJob);
			Fop fop = fopFactory
					.newFop(MimeConstants.MIME_FOP_PRINT, userAgent);
			fopTransform(doc, fop);
		}
	}

	private void fopTransform(Document doc, Fop fop) throws FOPException,
			TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		Transformer transformer = factory.newTransformer();
		Source src = new DOMSource(doc);
		Result res = new SAXResult(fop.getDefaultHandler());
		transformer.transform(src, res);
	}

	/**
	 * Устанавливает имя принтера.
	 * 
	 * @param printerName
	 *            Имя принтера.
	 */
	public void setPrinterName(String printerName) {
		this.printerName = printerName;
	}

}