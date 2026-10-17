package com.casarural.sistemapdv.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

/**
 * Acesso ao banco via pool de conexões (HikariCP).
 *
 * Cada operação deve pegar uma conexão e devolvê-la ao terminar, usando try-with-resources:
 *
 *     try (Connection conn = DB.getConnection()) { ... }
 *
 * O close() não fecha a conexão de verdade, apenas a devolve ao pool. O pool testa as conexões
 * e substitui as que o MySQL derrubou (wait_timeout, reinício do serviço, queda de rede).
 */
public class DB {

    private static final Logger log = LoggerFactory.getLogger(DB.class);

    private static HikariDataSource dataSource;

    private static synchronized HikariDataSource getDataSource() {
        if (dataSource == null) {
            Properties props = loadProperties();

            HikariConfig config = new HikariConfig();
            config.setPoolName("SistemaPDV");
            config.setJdbcUrl(props.getProperty("dburl"));
            config.setUsername(props.getProperty("user"));
            config.setPassword(props.getProperty("password"));

            // PDV de um caixa: poucas conexões bastam
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            // Tempo máximo esperando uma conexão antes de dar erro (em vez de travar a tela)
            config.setConnectionTimeout(5_000);
            // Renova as conexões bem antes do wait_timeout do MySQL (8h)
            config.setMaxLifetime(30 * 60_000);
            // Mantém as conexões ociosas vivas
            config.setKeepaliveTime(5 * 60_000);
            // Não falha ao iniciar se o MySQL estiver fora; tenta de novo na próxima operação
            config.setInitializationFailTimeout(-1);

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    public static Connection getConnection() {
        try {
            return getDataSource().getConnection();
        } catch (SQLException e) {
            // Detalhe técnico vai para o log; o usuário vê só o que fazer
            log.error("Falha ao conectar ao banco", e);
            throw new DbException(MENSAGEM_SEM_CONEXAO, e);
        }
    }

    public static final String MENSAGEM_SEM_CONEXAO =
            "Não foi possível conectar ao banco de dados.\n\n"
            + "Reinicie o computador e abra o sistema de novo.\n"
            + "Se continuar, entre em contato com o responsável pelo sistema.";

    public static final String MENSAGEM_ATUALIZACAO_FALHOU =
            "Não foi possível atualizar o banco de dados.\n\n"
            + "Não use o sistema e entre em contato com o responsável pelo sistema.";

    /**
     * Aplica as migrações pendentes (src/main/resources/db-migrations) antes de o sistema abrir.
     *
     * Usa o usuário de migração (migracao.user), que pode alterar a estrutura do banco, apenas
     * durante esta chamada; o resto do sistema usa o usuário comum (user), que só mexe em dados.
     *
     * - Banco vazio (computador da loja): cria o banco e todas as tabelas a partir da V1.
     * - Banco que já tinha tabelas sem histórico do Flyway (desenvolvimento): marca a V1 como
     *   baseline sem executá-la e aplica só as versões seguintes.
     */
    public static void migrate() {
        Properties props = loadProperties();
        String user = props.getProperty("migracao.user");
        String password = props.getProperty("migracao.password");
        if (user == null || user.isBlank()) {
            log.error("migracao.user não configurado no db.properties");
            throw new DbException(MENSAGEM_ATUALIZACAO_FALHOU);
        }
        if (password == null || password.isBlank()) {
            log.error("migracao.password não configurado no db.properties (linha ausente ou comentada com #)");
            throw new DbException(MENSAGEM_ATUALIZACAO_FALHOU);
        }

        // Só a conexão de migração pode criar o banco, se ele ainda não existir
        String url = props.getProperty("dburl");
        url += (url.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db-migrations")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .baselineDescription("estrutura existente antes do Flyway")
                    .load();
            MigrateResult result = flyway.migrate();
            log.info("Banco na versão {} ({} migração(ões) aplicada(s) agora)",
                    flyway.info().current().getVersion(), result.migrationsExecuted);
        } catch (FlywayException e) {
            log.error("Falha na migração do banco", e);
            throw new DbException(isConnectionFailure(e) ? MENSAGEM_SEM_CONEXAO : MENSAGEM_ATUALIZACAO_FALHOU, e);
        }
    }

    /** SQLState "08..." = falha de comunicação (MySQL parado, rede). */
    private static boolean isConnectionFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException) {
                String state = ((SQLException) t).getSQLState();
                if (state != null && state.startsWith("08")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /** Computador da loja: a senha fica fora do programa. */
    public static Path externalConfigPath() {
        String programData = System.getenv("ProgramData");
        if (programData == null || programData.isBlank()) {
            programData = "C:\\ProgramData";
        }
        return Paths.get(programData, "SistemaPDV", "db.properties");
    }

    /** Desenvolvimento: config/db.properties na pasta do projeto (fora do git e fora do programa). */
    public static Path devConfigPath() {
        return Paths.get("config", "db.properties").toAbsolutePath();
    }

    /**
     * Arquivo de configuração usado, nesta ordem:
     *  1. -Dpdv.config=caminho                       (se informado ao abrir o programa)
     *  2. C:\ProgramData\SistemaPDV\db.properties    (computador da loja)
     *  3. config\db.properties na pasta do projeto   (desenvolvimento na IDE)
     * Nunca de dentro do programa (.jar): assim nenhuma senha vai junto quando o programa é copiado.
     * Vazio se nenhum existir.
     */
    public static Optional<Path> arquivoConfiguracao() {
        String informado = System.getProperty("pdv.config");
        if (informado != null && !informado.isBlank()) {
            Path p = Paths.get(informado.trim());
            return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
        }
        for (Path p : new Path[]{externalConfigPath(), devConfigPath()}) {
            if (Files.isRegularFile(p)) return Optional.of(p);
        }
        return Optional.empty();
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        Optional<Path> arquivo = arquivoConfiguracao();
        if (arquivo.isEmpty()) {
            log.error("Nenhum db.properties encontrado. Procurado em: {} e {}{}", externalConfigPath(), devConfigPath(),
                    System.getProperty("pdv.config") == null ? "" : " e " + System.getProperty("pdv.config"));
            throw new DbException(MENSAGEM_SEM_CONFIGURACAO);
        }
        try (InputStream is = Files.newInputStream(arquivo.get())) {
            props.load(is);
        } catch (IOException e) {
            log.error("Erro ao ler {}", arquivo.get(), e);
            throw new DbException(MENSAGEM_SEM_CONFIGURACAO, e);
        }
        log.info("Configuração do banco: {}", arquivo.get());
        return props;
    }

    public static final String MENSAGEM_SEM_CONFIGURACAO =
            "O arquivo de configuração do banco não foi encontrado.\n\n"
            + "Não use o sistema e entre em contato com o responsável pelo sistema.";
}
