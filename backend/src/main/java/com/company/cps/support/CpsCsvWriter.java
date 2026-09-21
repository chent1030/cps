package com.company.cps.support;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CpsCsvWriter {

    private CpsCsvWriter() {
    }

    public static void write(
            HttpServletResponse response,
            String fileName,
            String[] headers,
            List<String[]> rows
    ) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writeLine(writer, headers);
        for (String[] row : rows) {
            writeLine(writer, row);
        }
        writer.flush();
    }

    private static void writeLine(PrintWriter writer, String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            String value = values[i] == null ? "" : values[i];
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }
}
