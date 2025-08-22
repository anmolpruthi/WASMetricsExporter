package com.score_me.was_metrics_exporter.service;

import com.score_me.was_metrics_exporter.dto.BulkMetricsDTO;
import com.score_me.was_metrics_exporter.helper.MethodHelper;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BulkExportMetrics {

    private final PgMetricsService pgMetricsService;
    private final DecimalFormat df = new DecimalFormat("#.##");

    @Value("${apiList.file.path}")
    private String metricsFilePath;

    public String exportMetricsBulk() throws IOException {
        validateFilePath();

        try (InputStream metricsFile = getMetricsFile(metricsFilePath);
             Workbook workbook = new XSSFWorkbook(metricsFile)) {

            Sheet sheet = workbook.getSheetAt(0);
            updateMetricsInSheet(sheet);

            saveWorkbook(workbook, new File(getOutputFile(metricsFilePath)));
            System.out.println("Excel updated successfully!");

        } catch (IOException e) {
            throw new IOException("Error processing Excel file: " + e.getMessage(), e);
        }

        return getOutputFile(metricsFilePath);
    }

    public ByteArrayResource exportMetricsToByteArray(InputStream file ) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            updateMetricsInSheet(sheet);

            workbook.write(outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        }
    }

    private void validateFilePath() {
        if (metricsFilePath == null) {
            throw new IllegalStateException("metricsFilePath is null");
        }
    }

//    private void updateMetricsInSheet(Sheet sheet) throws IOException {
//        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
//            Row row = sheet.getRow(i);
//            if (row != null) {
//                processRow(row, i);
//            }
//        }
//    }

    private void updateMetricsInSheet(Sheet sheet) throws IOException {
        int rowIndex = 1; // Start after header row (index 0)

        while (rowIndex <= sheet.getLastRowNum()) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                String groupId = extractGroupId(row, rowIndex);
                if (groupId != null) {
                    Map<String, Double> metrics = pgMetricsService.getMetricsForGroup(groupId);
                    if (metrics != null && !metrics.isEmpty()) {
                        rowIndex = writeMetricsVertically(sheet, row, metrics, rowIndex);
                        continue; //skip incrementing rowIndex here because it's updated in writeMetricsVertically
                    }
                }
            }
            rowIndex++; // move to next row if nothing was written
        }
    }

    private void processRow(Row row, int rowIndex) throws IOException {
        String groupId = extractGroupId(row, rowIndex);
        if (groupId == null) return;

        Map<String, Double> metrics = pgMetricsService.getMetricsForGroup(groupId);
        if (metrics == null || metrics.isEmpty()) {
            System.out.println("No metrics found for Group ID: " + groupId);
            return;
        }

        writeMetrics(row, metrics);
    }

    private String extractGroupId(Row row, int rowIndex) {
        Cell groupIdCell = row.getCell(2); // 3rd column
        if (groupIdCell == null) return null;

        String groupId = groupIdCell.getStringCellValue();
        if (groupId == null || groupId.isBlank()) {
            System.out.println("Group ID is empty at row " + (rowIndex + 1));
            return null;
        }
        return groupId;
    }

    private void writeMetrics(Row row, Map<String, Double> metrics) {
        String metricsString = metrics.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",\n "));

        Cell metricsCell = row.getCell(3); // 4th column
        if (metricsCell == null) {
            metricsCell = row.createCell(3);
        }
        metricsCell.setCellValue(metricsString);
    }
    /**
     * Writes metrics under the given row, expanding rows downward.
     */
//    private int writeMetricsVertically(Sheet sheet, Row baseRow, Map<String, Double> metrics, int startRowIndex) {
//        int currentRowIndex = startRowIndex;
//
//        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
//            Row row;
//            if (currentRowIndex == startRowIndex) {
//                // First metric: reuse base row
//                row = baseRow;
//            } else {
//                // Shift rows down if necessary (important if there are existing rows below)
//                sheet.shiftRows(currentRowIndex, sheet.getLastRowNum(), 1);
//
//                // Create new row
//                row = sheet.createRow(currentRowIndex);
//
//                // Optionally copy SNo, API name, PID for readability
//                row.createCell(0).setCellValue(baseRow.getCell(0).getNumericCellValue());
//                row.createCell(1).setCellValue(baseRow.getCell(1).getStringCellValue());
//                row.createCell(2).setCellValue(baseRow.getCell(2).getStringCellValue());
//            }
//
//            // Metric Name
//            row.createCell(3).setCellValue(entry.getKey());
//            // Metric Value
//            row.createCell(4).setCellValue(entry.getValue());
//
//            currentRowIndex++;
//        }
//        return currentRowIndex; // where the next SNo should continue
//    }
    private int writeMetricsVertically(Sheet sheet, Row baseRow, Map<String, Double> metrics, int startRowIndex) {
        int currentRowIndex = startRowIndex;
        boolean isFirstMetric = true;

        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            Row row;
            if (currentRowIndex == startRowIndex) {
                // First metric: reuse base row
                row = baseRow;
            } else {
                // Only shift if we are not past the last row
                if (currentRowIndex <= sheet.getLastRowNum()) {
                    sheet.shiftRows(currentRowIndex, sheet.getLastRowNum(), 1);
                }
                row = sheet.createRow(currentRowIndex);
            }

            if (isFirstMetric) {
                // Copy SNo, API name, PID only for the first metric
                copyCellValue(baseRow.getCell(0), row.createCell(0));
                copyCellValue(baseRow.getCell(1), row.createCell(1));
                copyCellValue(baseRow.getCell(2), row.createCell(2));
                isFirstMetric = false;
            }

            // Metric Name
            row.createCell(3).setCellValue(entry.getKey());
            // Metric Value
            row.createCell(4).setCellValue(entry.getValue());

            currentRowIndex++;
        }
        return currentRowIndex; // where the next API row should continue
    }


    /**
     * Helper: copy cell content safely (numeric/string).
     */
    private void copyCellValue(Cell source, Cell target) {
        if (source == null) return;
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            default -> target.setCellValue(source.toString());
        }
    }


    private void saveWorkbook(Workbook workbook, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            workbook.write(fos);
        }
    }

    private InputStream getMetricsFile(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location is empty");
        } else if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return resource.getInputStream();
        } else {
            Path p = Path.of(location);
            if (!Files.exists(p)) {
                throw new FileNotFoundException("File not found: " + location);
            }
            return new FileInputStream(location);
        }
    }

    private String getOutputFile(String inputPath) {
        if (inputPath.startsWith("classpath:")) {
            return "target/output.xlsx";
        } else {
            // Overwrite same file with "_output.xlsx"
            Path path = Path.of(inputPath);
            String filename = path.getFileName().toString();
            String newName = filename.replace(".xlsx", "_output.xlsx");
            return path.getParent().resolve(newName).toString();
        }
    }
}
