package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Informações de Ajuda > Sobre: versão do sistema, tecnologias, banco e onde ficam os arquivos.
 * Tudo em pares rótulo/valor, agrupados, para a tela e para o botão "Copiar" (mandar ao suporte).
 */
public class SobreService {

    public static final String ARQUIVO = "/com/casarural/sistemapdv/ajuda/sobre.properties";
    private static final String DESENVOLVIMENTO = "(desenvolvimento)";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Properties props = new Properties();

    public SobreService() {
        try (InputStream in = SobreService.class.getResourceAsStream(ARQUIVO)) {
            if (in != null) props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            // sem o arquivo a tela mostra "(desenvolvimento)" nos campos
        }
    }

    /** Valor do sobre.properties; o que o Maven não trocou (rodando pela IDE) vira "(desenvolvimento)". */
    public String valor(String chave) {
        String v = props.getProperty(chave);
        if (v == null || v.isBlank() || v.contains("${")) return DESENVOLVIMENTO;
        return v.trim();
    }

    /** "1.0-SNAPSHOT" → "1.0 (em desenvolvimento)". */
    public String versao() {
        String v = valor("sistema.versao");
        return v.endsWith("-SNAPSHOT") ? v.replace("-SNAPSHOT", "") + " (em desenvolvimento)" : v;
    }

    public Map<String, List<String[]>> grupos() {
        Map<String, List<String[]>> g = new LinkedHashMap<>();

        List<String[]> sistema = new ArrayList<>();
        sistema.add(par("Versão", versao()));
        sistema.add(par("Compilado em", valor("sistema.compilado")));
        sistema.add(par("Feito por", valor("sistema.autor")));
        g.put("Sistema", sistema);

        g.put("Banco de dados", banco());

        List<String[]> tec = new ArrayList<>();
        tec.add(par("Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"));
        String javafx = System.getProperty("javafx.runtime.version");
        tec.add(par("JavaFX (telas)", javafx != null ? javafx : valor("lib.javafx")));
        tec.add(par("MySQL Connector/J", valor("lib.mysql.connector")));
        tec.add(par("HikariCP (conexões)", valor("lib.hikari")));
        tec.add(par("Flyway (atualiza o banco)", valor("lib.flyway")));
        tec.add(par("SLF4J + Logback (logs)", valor("lib.slf4j") + " / " + valor("lib.logback")));
        tec.add(par("Sistema operacional", System.getProperty("os.name") + " " + System.getProperty("os.version")));
        g.put("Tecnologias", tec);

        List<String[]> arquivos = new ArrayList<>();
        java.nio.file.Path config = DB.externalConfigPath();
        arquivos.add(par("Configuração do banco",
                Files.isRegularFile(config) ? config.toString() : "db.properties do projeto (desenvolvimento)"));
        arquivos.add(par("Logs", pastaLogs()));
        g.put("Arquivos", arquivos);
        return g;
    }

    /** Texto simples de tudo, para copiar e mandar a quem der suporte. */
    public String texto(Map<String, List<String[]>> grupos) {
        StringBuilder sb = new StringBuilder(valor("sistema.nome") + " - " + valor("sistema.loja") + "\n");
        grupos.forEach((titulo, linhas) -> {
            sb.append('\n').append(titulo).append('\n');
            for (String[] l : linhas) sb.append("  ").append(l[0]).append(": ").append(l[1]).append('\n');
        });
        return sb.toString();
    }

    private List<String[]> banco() {
        List<String[]> b = new ArrayList<>();
        try (Connection conn = DB.getConnection(); Statement st = conn.createStatement()) {
            b.add(par("Banco", conn.getCatalog()));
            try (ResultSet rs = st.executeQuery("SELECT VERSION()")) {
                if (rs.next()) b.add(par("MySQL", rs.getString(1)));
            }
            // Versão da estrutura: última migração do Flyway aplicada com sucesso
            try (ResultSet rs = st.executeQuery(
                    "SELECT version, description, installed_on FROM flyway_schema_history " +
                    "WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")) {
                if (rs.next()) {
                    Timestamp quando = rs.getTimestamp(3);
                    b.add(par("Versão do banco", "V" + rs.getString(1) + " - " + rs.getString(2)
                            + (quando == null ? "" : " (desde " + quando.toLocalDateTime().format(DATA_HORA) + ")")));
                }
            }
            String url = conn.getMetaData().getURL();
            if (url != null) b.add(par("Servidor", url.replaceFirst("^jdbc:mysql://", "").replaceFirst("[/?].*$", "")));
        } catch (SQLException | RuntimeException e) {
            b.add(par("Situação", "sem conexão com o banco"));
        }
        return b;
    }

    private static String pastaLogs() {
        String definida = System.getProperty("pdv.logdir");
        if (definida != null && !definida.isBlank()) return definida;
        String programData = System.getenv("ProgramData");
        return Paths.get(programData == null || programData.isBlank() ? "C:\\ProgramData" : programData,
                "SistemaPDV", "logs").toString();
    }

    private static String[] par(String rotulo, String valor) {
        return new String[]{rotulo, valor == null ? "" : valor};
    }
}
