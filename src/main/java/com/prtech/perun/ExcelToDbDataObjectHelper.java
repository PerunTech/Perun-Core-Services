package com.prtech.perun;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.prtech.svarog_common.DbDataObject;

public class ExcelToDbDataObjectHelper {
	private static final Logger log4j = LogManager.getLogger(ExcelToDbDataObjectHelper.class);
	private static final String HEADER_SEPARATOR = "---";

	private Long objectType;

	public ExcelToDbDataObjectHelper() {
	}

	public ExcelToDbDataObjectHelper(Long objectType) {
		this.objectType = objectType;
	}

	/**
	 * Reads every sheet in the workbook at {@code excelFilePath} and converts each
	 * data row into a {@link DbDataObject}.
	 *
	 * @param excelFilePath absolute or relative path to the {@code .xlsx} file
	 * @return a (possibly empty) list of {@link DbDataObject} instances; never
	 *         {@code null}
	 * @throws IOException if the file cannot be opened or read
	 */
	public List<DbDataObject> convertExcelToDbDataObjects(String excelFilePath) throws IOException {
		if (excelFilePath == null || excelFilePath.trim().isEmpty()) {
			throw new IllegalArgumentException("excelFilePath must not be null or empty");
		}
		log4j.info("Loading workbook from file path: '{}'", excelFilePath);
		try (InputStream fis = new FileInputStream(excelFilePath)) {
			return convertFromStream(fis);
		} catch (IOException e) {
			log4j.error("Failed to read Excel file '{}': {}", excelFilePath, e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Reads every sheet in the workbook supplied as a raw {@code byte[]} and
	 * converts each data row into a {@link DbDataObject}.
	 *
	 * @param excelBytes the raw bytes of a valid {@code .xlsx} workbook; must not
	 *                   be {@code null} or empty
	 * @return a (possibly empty) list of {@link DbDataObject} instances; never
	 *         {@code null}
	 * @throws IOException              if the byte content cannot be parsed as a
	 *                                  workbook
	 * @throws IllegalArgumentException if {@code excelBytes} is {@code null} or
	 *                                  empty
	 */
	public List<DbDataObject> convertExcelToDbDataObjects(byte[] excelBytes) throws IOException {
		if (excelBytes == null || excelBytes.length == 0) {
			throw new IllegalArgumentException("excelBytes must not be null or empty");
		}
		log4j.info("Loading workbook from byte array ({} bytes)", excelBytes.length);
		try (InputStream bis = new ByteArrayInputStream(excelBytes)) {
			return convertFromStream(bis);
		} catch (IOException e) {
			log4j.error("Failed to parse Excel workbook from byte array: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Opens a {@link Workbook} from the given {@link InputStream}, iterates every
	 * sheet, and merges all rows into a single result list.
	 *
	 * @param inputStream the stream to read; the caller is responsible for closing
	 *                    it after this method returns
	 * @return merged list of {@link DbDataObject} instances from all sheets
	 * @throws IOException if the workbook cannot be read or parsed
	 */
	private List<DbDataObject> convertFromStream(InputStream inputStream) throws IOException {
		List<DbDataObject> result = new ArrayList<>();

		try (Workbook workbook = WorkbookFactory.create(inputStream)) {
			int sheetCount = workbook.getNumberOfSheets();

			for (int s = 0; s < sheetCount; s++) {
				Sheet sheet = workbook.getSheetAt(s);
				List<DbDataObject> sheetRows = processSheet(sheet);
				log4j.info("Sheet '{}' yielded {} record(s).", sheet.getSheetName(), sheetRows.size());
				result.addAll(sheetRows);
			}
		}

		return result;
	}

	/**
	 * Processes a single {@link Sheet}: reads the header row (row 0) to build the
	 * field-name array, then converts every subsequent non-empty row into a
	 * {@link DbDataObject}.
	 *
	 * @param sheet the sheet to process
	 * @return list of {@link DbDataObject} instances parsed from the sheet
	 */
	private List<DbDataObject> processSheet(Sheet sheet) {
		List<DbDataObject> records = new ArrayList<>();

		Row headerRow = sheet.getRow(0);
		if (headerRow == null) {
			log4j.warn("Sheet '{}' has no header row — skipping.", sheet.getSheetName());
			return records;
		}

		String[] fieldNames = extractFieldNames(headerRow);
		if (fieldNames.length == 0) {
			log4j.warn("Sheet '{}' header row is empty — skipping.", sheet.getSheetName());
			return records;
		}

		int lastRowNum = sheet.getLastRowNum();
		for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
			Row row = sheet.getRow(rowIndex);
			if (isRowEmpty(row)) {
				continue;
			}
			try {
				DbDataObject dbo = rowToDbDataObject(row, fieldNames);
				records.add(dbo);
			} catch (Exception e) {
				log4j.error("Error converting row {} on sheet '{}' — row skipped: {}", rowIndex, sheet.getSheetName(),
						e.getMessage(), e);
			}
		}

		return records;
	}

	/**
	 * Reads the header row and extracts the technical field name from each cell by
	 * stripping everything from {@value #HEADER_SEPARATOR} onward.
	 *
	 * @param headerRow the first row of a sheet
	 * @return array of field names, one per column
	 */
	private String[] extractFieldNames(Row headerRow) {
		int cellCount = headerRow.getLastCellNum();
		if (cellCount < 0) {
			return new String[0];
		}

		String[] fieldNames = new String[cellCount];
		for (int col = 0; col < cellCount; col++) {
			Cell cell = headerRow.getCell(col);
			String rawHeader = (cell != null) ? getCellStringValue(cell).trim() : "";
			int sepIdx = rawHeader.indexOf(HEADER_SEPARATOR);
			fieldNames[col] = (sepIdx >= 0) ? rawHeader.substring(0, sepIdx).trim() : rawHeader;
		}
		return fieldNames;
	}

	/**
	 * Converts a single data {@link Row} into a {@link DbDataObject}.
	 *
	 * @param row        the data row
	 * @param fieldNames the field names indexed to match each column position
	 * @return a populated {@link DbDataObject}
	 */
	private DbDataObject rowToDbDataObject(Row row, String[] fieldNames) {
		DbDataObject dbo = new DbDataObject();

		if (this.objectType != null) {
			dbo.setObjectType(this.objectType);
		}

		for (int col = 0; col < fieldNames.length; col++) {
			String fieldName = fieldNames[col];
			if (fieldName == null || fieldName.isEmpty()) {
				continue;
			}

			Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
			Object cellValue = extractCellValue(cell);

			switch (fieldName) {
			case "PKID":
				if (cellValue != null) {
					dbo.setPkid(toLong(cellValue));
				}
				break;

			case "OBJECT_ID":
				if (cellValue != null) {
					dbo.setObjectId(toLong(cellValue));
				}
				break;

			default:
				dbo.setVal(fieldName, cellValue);
				break;
			}
		}

		return dbo;
	}

	/**
	 * Extracts the value from a {@link Cell} as the most appropriate Java type:
	 * 
	 * @param cell the cell to read; may be {@code null}
	 * @return the cell's value as a Java object, or {@code null}
	 */
	private Object extractCellValue(Cell cell) {
		if (cell == null) {
			return null;
		}

		CellType effectiveType = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType()
				: cell.getCellType();

		switch (effectiveType) {
		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				return cell.getLocalDateTimeCellValue().toString();
			}
			double numericVal = cell.getNumericCellValue();
			if (numericVal == Math.floor(numericVal) && !Double.isInfinite(numericVal) && numericVal >= Long.MIN_VALUE
					&& numericVal <= Long.MAX_VALUE) {
				return (long) numericVal;
			}
			return numericVal;

		case BOOLEAN:
			return cell.getBooleanCellValue();

		case STRING:
			String strVal = cell.getStringCellValue().trim();
			return strVal.isEmpty() ? null : strVal;

		case BLANK:
		case _NONE:
		case ERROR:
		default:
			return null;
		}
	}

	/**
	 * Returns the string representation of a cell regardless of its type. Used only
	 * for reading header cells.
	 *
	 * @param cell the cell to read; must not be {@code null}
	 * @return string value of the cell
	 */
	private String getCellStringValue(Cell cell) {
		switch (cell.getCellType()) {
		case STRING:
			return cell.getStringCellValue();
		case NUMERIC:
			return String.valueOf((long) cell.getNumericCellValue());
		case BOOLEAN:
			return String.valueOf(cell.getBooleanCellValue());
		case FORMULA:
			return cell.getCellFormula();
		default:
			return "";
		}
	}

	/**
	 * Converts a cell value ({@code Long}, {@code Double}, or {@code String}) to
	 * {@code Long}. Used exclusively for the {@code PKID} and {@code OBJECT_ID}
	 * columns.
	 *
	 * @param value the value to convert; must not be {@code null}
	 * @return the long representation
	 * @throws NumberFormatException if a {@code String} value cannot be parsed
	 * @throws ClassCastException    if the type is unexpected
	 */
	private Long toLong(Object value) {
		if (value instanceof Long) {
			return (Long) value;
		}
		if (value instanceof Double) {
			return ((Double) value).longValue();
		}
		return Long.parseLong(value.toString());
	}

	/**
	 * Returns {@code true} when {@code row} is {@code null} or every cell in it is
	 * blank/null.
	 *
	 * @param row the row to inspect; may be {@code null}
	 * @return {@code true} if the row has no usable content
	 */
	private boolean isRowEmpty(Row row) {
		if (row == null) {
			return true;
		}
		for (int col = row.getFirstCellNum(); col < row.getLastCellNum(); col++) {
			Cell cell = row.getCell(col);
			if (cell != null && cell.getCellType() != CellType.BLANK) {
				return false;
			}
		}
		return true;
	}
}