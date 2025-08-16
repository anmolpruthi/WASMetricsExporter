package com.score_me.was_metrics_exporter.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Metric Name");
            header.createCell(1).setCellValue("Value");
        }

        Map<String, Row> existingRows = new LinkedHashMap<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getCell(0) != null) {
                existingRows.put(row.getCell(0).getStringCellValue(), row);
            }
        }

        int lastRowNum = sheet.getLastRowNum();
        for (Map.Entry<String, Double> entry : newMetrics.entrySet()) {
            if (existingRows.containsKey(entry.getKey())) {
                existingRows.get(entry.getKey()).getCell(1)
                        .setCellValue(Double.parseDouble(df.format(entry.getValue())));
            } else {
                Row row = sheet.createRow(++lastRowNum);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(Double.parseDouble(df.format(entry.getValue())));
            }
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            workbook.write(fos);
        }
        workbook.close();
    }
}
