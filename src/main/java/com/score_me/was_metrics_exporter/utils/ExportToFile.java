package com.score_me.was_metrics_exporter.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.*;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExportToFile is a utility class for exporting metrics to text and Excel files.
 * It provides methods to export metrics to a text file and an Excel file.
 * The class is designed to be used as a Spring component.
 */
@Slf4j
@Component
public class ExportToFile {

    private static final DecimalFormat df = new DecimalFormat("0.00");

    public static void exportToTxt(Map<String, Double> newMetrics, String filename) throws IOException {
        log.info("Exporting to txt file...");
        File file = new File(filename);
        Map<String, Double> metrics = new LinkedHashMap<>();
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        Double value = Double.parseDouble(parts[1].trim());
                        metrics.put(key, value);
                    }
                }
            } catch (Exception e) {
                log.error("Error reading metrics from text file {}", filename, e);
            }

            metrics.putAll(newMetrics);
        }
        try (FileWriter writer = new FileWriter(file, false)) {
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                writer.write(entry.getKey() + " = " + df.format(entry.getValue()) + "\n");
            }
        } catch (IOException e) {
            log.error("Error writing metrics to text file {}", filename, e);
        }
    }

//    public static void exportToExcel(Map<String, Double> newMetrics, String fileName) throws IOException {
//        File file = new File(fileName);
//        Workbook workbook;
//        Sheet sheet;
//
//        if (file.exists()) {
//            try (FileInputStream fis = new FileInputStream(file)) {
//                workbook = new XSSFWorkbook(fis);
//            }
//            sheet = workbook.getSheet("Metrics");
//            if (sheet == null) {
//                sheet = workbook.createSheet("Metrics");
//            }
//        } else {
//            workbook = new XSSFWorkbook();
//            sheet = workbook.createSheet("Metrics");
//            Row header = sheet.createRow(0);
////            Cell headerColumn1 = header.createCell(0);
////            Cell headerColumn2 = header.createCell(1);
////            headerColumn1.setCellValue("Metric Name");
////            headerColumn2.setCellValue("Value");
//            header.createCell(0).setCellValue("Metric Name");
//            header.createCell(1).setCellValue("Value");
//        }
//
//        Map<String, Row> existingRows = new LinkedHashMap<>();
//        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
//            Row row = sheet.getRow(i);
//            if (row != null && row.getCell(0) != null) {
//                existingRows.put(row.getCell(0).getStringCellValue(), row);
//            }
//        }
//
//        int lastRowNum = sheet.getLastRowNum();
//        for (Map.Entry<String, Double> entry : newMetrics.entrySet()) {
//            if (existingRows.containsKey(entry.getKey())) {
//                existingRows.get(entry.getKey()).getCell(1)
//                        .setCellValue(Double.parseDouble(df.format(entry.getValue())));
//            } else {
//                Row row = sheet.createRow(++lastRowNum);
//                row.createCell(0).setCellValue(entry.getKey());
//                row.createCell(1).setCellValue(Double.parseDouble(df.format(entry.getValue())));
//            }
//        }
//
//        sheet.autoSizeColumn(0);
//        sheet.autoSizeColumn(1);
//
//        try (FileOutputStream fos = new FileOutputStream(fileName)) {
//            workbook.write(fos);
//        }
//        workbook.close();
//    }


// ...

    public static void exportToExcel(Map<String, Double> newMetrics, String fileName) throws IOException {
        File file = new File(fileName);
        Workbook workbook;
        Sheet sheet;

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                workbook = new XSSFWorkbook(fis);
            }
            sheet = workbook.getSheet("Metrics");
            if (sheet == null) {
                sheet = workbook.createSheet("Metrics");
            }
        } else {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet("Metrics");
            sheet.createRow(0); // header row
            sheet.createRow(1); // values row
        }

        Row headerRow = sheet.getRow(0);
        Row valueRow = sheet.getRow(1);

        if (headerRow == null) {
            headerRow = sheet.createRow(0);
        }
        if (valueRow == null) {
            valueRow = sheet.createRow(1);
        }

        CellStyle headerStyle = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        // Build map of existing headers (metric name -> column index)
        Map<String, Integer> headerIndexMap = new LinkedHashMap<>();
        for (int col = 0; col < headerRow.getLastCellNum(); col++) {
            Cell cell = headerRow.getCell(col);
            if (cell != null) {
                headerIndexMap.put(cell.getStringCellValue(), col);
            }
        }

        int lastColNum = headerRow.getLastCellNum();
        if (lastColNum < 0) lastColNum = 0; // empty sheet case

        for (Map.Entry<String, Double> entry : newMetrics.entrySet()) {
            // ✅ Convert to CamelCase
            String metricName = toCamelCase(entry.getKey());
            double metricValue = Double.parseDouble(df.format(entry.getValue()));

            Integer colIndex = headerIndexMap.get(metricName);
            if (colIndex == null) {
                // Add new column at the end
                colIndex = lastColNum++;
                Cell headerCell = headerRow.createCell(colIndex);
                headerCell.setCellValue(metricName);
                headerCell.setCellStyle(headerStyle); // apply bold style
            }

            Cell valueCell = valueRow.getCell(colIndex);
            if (valueCell == null) {
                valueCell = valueRow.createCell(colIndex);
            }
            valueCell.setCellValue(metricValue);
        }

        // Auto-size all used columns
        for (int i = 0; i < lastColNum; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            workbook.write(fos);
            workbook.close();
        }
    }

    // ✅ Helper to convert snake_case or lower-case to CamelCase
    private static String toCamelCase(String input) {
        StringBuilder result = new StringBuilder();
        for (String part : input.split("_")) {
            if (part.isEmpty()) continue;
            result.append(part.substring(0, 1).toUpperCase());
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }


}
