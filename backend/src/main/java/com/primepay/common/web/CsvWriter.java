package com.primepay.common.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Small RFC 4180 CSV builder for report downloads. Cells starting with = + - @ are prefixed with a quote
 * so spreadsheet apps don't run them as formulas (CSV injection).
 */
public class CsvWriter {
    private final List<String> lines = new ArrayList<>();

    public CsvWriter(String... headers) {
        row((Object[]) headers);
    }

    public CsvWriter row(Object... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) line.append(',');
            line.append(escape(cells[i]));
        }
        lines.add(line.toString());
        return this;
    }

    public ResponseEntity<byte[]> download(String fileName) {
        byte[] body = ("﻿" + String.join("\r\n", lines) + "\r\n").getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static String escape(Object value) {
        if (value == null) return "";
        String text = value instanceof java.math.BigDecimal d ? d.toPlainString() : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
