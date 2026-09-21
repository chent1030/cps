package com.company.cps.support;

import com.alibaba.excel.EasyExcel;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CpsExcelWriter {

    private CpsExcelWriter() {
    }

    public static void write(
            HttpServletResponse response,
            String fileName,
            String sheetName,
            String[] headers,
            List<String[]> rows
    ) throws IOException {
        response.reset();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        List<List<String>> head = new ArrayList<>();
        for (String header : headers) {
            head.add(Collections.singletonList(header));
        }
        List<List<String>> data = new ArrayList<>();
        for (String[] row : rows) {
            List<String> values = new ArrayList<>();
            for (String value : row) {
                values.add(value == null ? "" : value);
            }
            data.add(values);
        }
        EasyExcel.write(response.getOutputStream()).head(head).sheet(sheetName).doWrite(data);
    }

    public static void writeTemplate(HttpServletResponse response, String fileName, String sheetName, String[] headers) throws IOException {
        write(response, fileName, sheetName, headers, Collections.emptyList());
    }
}
