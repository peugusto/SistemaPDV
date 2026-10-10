package com.casarural.sistemapdv.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
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
            throw new DbException("Não foi possível conectar ao banco de dados. "
                    + "Verifique se o MySQL está em execução.\nDetalhe: " + e.getMessage());
        }
    }

    public static synchronized void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private static Properties loadProperties() {
        try (InputStream is = DB.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (is == null) {
                throw new DbException("Arquivo db.properties não encontrado.");
            }
            Properties props = new Properties();
            props.load(is);
            return props;
        } catch (IOException e) {
            throw new DbException(e.getMessage());
        }
    }
}
