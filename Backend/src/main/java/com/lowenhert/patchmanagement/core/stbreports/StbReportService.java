package com.lowenhert.patchmanagement.core.stbreports;

import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.stbreports.dto.StbReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StbReportService {

    private final StbReportRepository repository;

    public StbReportResponse upload(MultipartFile file, Integer stbNo, String boxName) throws IOException {
        byte[] fileBytes = file.getBytes();

        // Parse Excel to extract test counts
        int totalTests = 0;
        int passed = 0;
        int failed = 0;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            int statusColIndex = findStatusColumn(sheet);
            if (statusColIndex >= 0) {
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    Cell cell = row.getCell(statusColIndex);
                    if (cell == null) continue;
                    String value = getCellStringValue(cell).trim().toUpperCase();
                    if (!value.isEmpty()) {
                        totalTests++;
                        if (value.equals("PASSED") || value.equals("PASS")) {
                            passed++;
                        } else if (value.equals("FAILED") || value.equals("FAIL")) {
                            failed++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse Excel for test counts: {}", e.getMessage());
        }

        StbReport report = StbReport.builder()
                .stbNo(stbNo)
                .boxName(boxName)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileData(fileBytes)
                .reportDate(LocalDate.now().toString())
                .totalTests(totalTests)
                .passed(passed)
                .failed(failed)
                .build();

        repository.save(report);
        log.info("Report uploaded: id={}, stbNo={}, boxName={}, fileName={}, total={}, passed={}, failed={}",
                report.getId(), stbNo, boxName, file.getOriginalFilename(), totalTests, passed, failed);
        return mapToResponse(report);
    }

    public List<StbReportResponse> getAll() {
        List<StbReportResponse> result = repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        log.debug("Fetched {} reports", result.size());
        return result;
    }

    public List<StbReportResponse> getByStbNo(Integer stbNo) {
        List<StbReportResponse> result = repository.findByStbNo(stbNo)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        log.debug("Fetched {} reports for stbNo={}", result.size(), stbNo);
        return result;
    }

    public StbReport getReportEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Report not found: id={}", id);
                    return new ResourceNotFoundException("Report not found with id: " + id);
                });
    }

    public void delete(Long id) {
        StbReport report = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete failed — Report not found: id={}", id);
                    return new ResourceNotFoundException("Report not found with id: " + id);
                });
        repository.delete(report);
        log.info("Report deleted: id={}", id);
    }

    private StbReportResponse mapToResponse(StbReport report) {
        return StbReportResponse.builder()
                .id(report.getId())
                .stbNo(report.getStbNo())
                .boxName(report.getBoxName())
                .fileName(report.getFileName())
                .contentType(report.getContentType())
                .reportDate(report.getReportDate())
                .totalTests(report.getTotalTests() != null ? report.getTotalTests() : 0)
                .passed(report.getPassed() != null ? report.getPassed() : 0)
                .failed(report.getFailed() != null ? report.getFailed() : 0)
                .uploadedAt(report.getUploadedAt())
                .build();
    }

    private int findStatusColumn(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) return -1;
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            if (cell != null) {
                String val = getCellStringValue(cell).trim().toUpperCase();
                if (val.equals("STATUS") || val.equals("RESULT") || val.contains("PASS/FAIL")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }
}
