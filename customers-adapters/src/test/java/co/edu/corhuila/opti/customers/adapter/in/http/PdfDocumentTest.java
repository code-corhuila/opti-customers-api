package co.edu.corhuila.opti.customers.adapter.in.http;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class PdfDocumentTest {
    @Test void paginatesAndEscapesTextWithAccurateByteOffsets() {
        var rows = new ArrayList<List<String>>();
        for (int i = 0; i < 100; i++) rows.add(List.of("Línea " + i, "Medición"));
        var document = new PdfDocument("Fórmula óptica (prueba) \\ ÁÉÍÓÚ ñ", "Datos ficticios");
        document.table(new String[]{"Referencia", "Detalle"}, new int[]{250, 261}, rows);
        byte[] bytes = document.render();
        String pdf = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        assertThat(pdf).startsWith("%PDF-1.4").contains("/Count 6", "Fórmula óptica \\(prueba\\)", "Línea 99", "Página 6 de 6", "%%EOF");
        assertThat(pdf.split("\\(Referencia\\)", -1)).hasSize(7);
        int offset = Integer.parseInt(pdf.substring(pdf.indexOf("startxref\n") + 10).trim().split("\n")[0]);
        assertThat(new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("xref");
    }
}
