package com.prtech.reports;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.prtech.svarog.SvException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.ExporterInput;
import net.sf.jasperreports.export.OutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;

public class GeneratePrint {
	static final Logger log4j = LogManager.getLogger(GeneratePrint.class.getName());
	/**
	 * Prevents concurrent requests in this JVM from compiling and reading the same
	 * .jasper files at the same time.
	 */
	private static final Object REPORT_COMPILE_LOCK = new Object();
	/**
	 * Finds literal subreport file names inside Jasper subreport expressions, for
	 * example "subreport_ugrl_result.jasper".
	 */
	private static final Pattern SUBREPORT_JASPER_EXPRESSION = Pattern.compile("\"([^\"]+\\.jasper)\"");
	/**
	 * Finds subreportExpression XML blocks in JRXML content. The content inside the
	 * block is later inspected for literal .jasper references.
	 */
	private static final Pattern SUBREPORT_EXPRESSION = Pattern.compile("<subreportExpression[^>]*>(.*?)</subreportExpression>",
			Pattern.DOTALL);

	public static void executeReport(Properties prop, String reportName, String exportDocType,
			OutputStream outputStream, Connection conn) {
		executeReport(null, prop, reportName, exportDocType, outputStream, conn);
	}

	public static void executeReport(JasperReport jReport, Properties prop, String reportName, String exportDocType,
			OutputStream outputStream, Connection conn) {
		HashMap<String, Object> hm = new HashMap<>();
		try {
			String path = prop.getProperty("path");
			if (path == null)
				throw new SvException("error.path_not_found", null);
			synchronized (REPORT_COMPILE_LOCK) {
				compileReportAndSubreports(Paths.get(path), reportName, new HashSet<String>());
			}
			log4j.trace("Recompiling done!");
			String jasperFileName = Paths.get(path, reportName + ".jasper").toString();
			if (conn != null && conn.isClosed())
				log4j.error("Database connection is closed!");
			for (Map.Entry<Object, Object> proprs : prop.entrySet())
				hm.put((String) proprs.getKey(), proprs.getValue());
			if (exportDocType.equals("EXCEL"))
				hm.put("IS_IGNORE_PAGINATION", Boolean.valueOf(true));
			JasperPrint jPrint = null;
			if (jReport != null) {
				jPrint = JasperFillManager.fillReport(jReport, hm, conn);
			} else {
				jPrint = JasperFillManager.fillReport(jasperFileName, hm, conn);
			}
			if (exportDocType.equals("PDF")) {
				generatePdf(reportName, jPrint, outputStream);
			} else if (exportDocType.equals("EXCEL")) {
				generateXlsx(jPrint, outputStream);
			}
		} catch (Exception e) {
			log4j.error("Generating {} failed!", exportDocType, e);
		}
	}

	private static void generatePdf(String reportName, JasperPrint print, OutputStream out) throws JRException {
		JRPdfExporter exporter = new JRPdfExporter();
		exporter.setExporterInput((ExporterInput) new SimpleExporterInput(print));
		exporter.setExporterOutput((OutputStreamExporterOutput) new SimpleOutputStreamExporterOutput(out));
		SimplePdfExporterConfiguration configuration = new SimplePdfExporterConfiguration();
		configuration.setMetadataTitle(reportName + ".pdf");
		exporter.setConfiguration(configuration);
		exporter.exportReport();
	}

	private static void generateXlsx(JasperPrint print, OutputStream out) throws JRException {
		JRXlsxExporter exporter = new JRXlsxExporter();
		exporter.setExporterInput((ExporterInput) new SimpleExporterInput(print));
		exporter.setExporterOutput((OutputStreamExporterOutput) new SimpleOutputStreamExporterOutput(out));
		SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
		configuration.setWhitePageBackground(Boolean.valueOf(false));
		configuration.setDetectCellType(Boolean.valueOf(false));
		exporter.setConfiguration(configuration);
		exporter.exportReport();
	}

	/**
	 * Compiles the requested report and any literal .jasper subreports it references.
	 * Compilation is skipped when the .jasper file exists and is newer than the
	 * matching .jrxml file.
	 *
	 * @param reportDir      directory that contains report templates
	 * @param reportName     report name without extension
	 * @param visitedReports already visited .jasper paths, used to avoid cycles
	 */
	private static void compileReportAndSubreports(Path reportDir, String reportName, Set<String> visitedReports) {
		Path jasperPath = reportDir.resolve(reportName + ".jasper").normalize();
		Path jrxmlPath = reportDir.resolve(reportName + ".jrxml").normalize();
		compileReportAndSubreports(reportDir, jrxmlPath, jasperPath, visitedReports);
	}

	/**
	 * Compiles a report by explicit JRXML/Jasper paths, then recursively compiles
	 * literal subreport references found in that JRXML.
	 *
	 * @param reportDir      base report directory used to resolve relative subreport
	 *                       paths
	 * @param jrxmlPath      path to the JRXML source
	 * @param jasperPath     path to the compiled Jasper output
	 * @param visitedReports already visited .jasper paths, used to avoid duplicate
	 *                       work and recursive loops
	 */
	private static void compileReportAndSubreports(Path reportDir, Path jrxmlPath, Path jasperPath,
			Set<String> visitedReports) {
		String reportKey = jasperPath.toAbsolutePath().normalize().toString();
		if (!visitedReports.add(reportKey))
			return;

		File jrxml = jrxmlPath.toFile();
		File jasper = jasperPath.toFile();
		compileReportIfNeeded(jrxml, jasper);

		for (String subreportJasper : getLiteralSubreportExpressions(jrxml)) {
			Path subreportJasperPath = Paths.get(subreportJasper);
			if (!subreportJasperPath.isAbsolute())
				subreportJasperPath = reportDir.resolve(subreportJasperPath);
			subreportJasperPath = subreportJasperPath.normalize();

			String subreportFileName = subreportJasperPath.getFileName().toString();
			String subreportJrxmlName = subreportFileName.substring(0, subreportFileName.length() - ".jasper".length())
					+ ".jrxml";
			Path subreportJrxmlPath = subreportJasperPath.resolveSibling(subreportJrxmlName).normalize();
			compileReportAndSubreports(reportDir, subreportJrxmlPath, subreportJasperPath, visitedReports);
		}
	}

	/**
	 * Compiles one JRXML file only when its compiled Jasper file is missing or older
	 * than the JRXML source.
	 *
	 * @param jrxml  JRXML source file
	 * @param jasper compiled Jasper target file
	 */
	private static void compileReportIfNeeded(File jrxml, File jasper) {
		if (!jrxml.exists()) {
			log4j.warn("Report JRXML not found {}", jrxml.getAbsolutePath());
			return;
		}
		if (!jasper.exists() || jrxml.lastModified() > jasper.lastModified()) {
			try {
				JasperCompileManager.compileReportToFile(jrxml.getAbsolutePath(), jasper.getAbsolutePath());
			} catch (Exception e) {
				log4j.error("Failed to compile report {}", jrxml.getAbsolutePath(), e);
			}
		}
	}

	/**
	 * Extracts literal .jasper filenames from subreportExpression blocks in a JRXML
	 * file. Dynamic expressions are ignored because their target cannot be known
	 * safely before report execution.
	 *
	 * @param jrxml JRXML file to inspect
	 * @return literal .jasper subreport paths referenced by the report
	 */
	private static Set<String> getLiteralSubreportExpressions(File jrxml) {
		Set<String> subreports = new HashSet<>();
		if (!jrxml.exists())
			return subreports;
		try {
			String jrxmlContent = new String(Files.readAllBytes(jrxml.toPath()), StandardCharsets.UTF_8);
			Matcher expressionMatcher = SUBREPORT_EXPRESSION.matcher(jrxmlContent);
			while (expressionMatcher.find()) {
				Matcher matcher = SUBREPORT_JASPER_EXPRESSION.matcher(expressionMatcher.group(1));
				while (matcher.find())
					subreports.add(matcher.group(1));
			}
		} catch (Exception e) {
			log4j.error("Failed to inspect subreports for {}", jrxml.getAbsolutePath(), e);
		}
		return subreports;
	}
}
