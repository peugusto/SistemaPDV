package com.casarural.sistemapdv.db;

import org.flywaydb.core.Flyway;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Entrada separada, chamada apenas pelo instalador elevado ou pela manutenção administrativa. */
public final class MigracaoBanco {
    private MigracaoBanco() {}

    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            Properties migracao = separarConfiguracoes(Path.of(args[0]), Path.of(args[1]));
            atualizar(migracao);
            System.out.println("Banco preparado. Credenciais de execução e migração separadas.");
        } catch (Exception e) {
            // Não imprime propriedades, linha de comando ou causas que possam conter credenciais.
            System.err.println("Não foi possível preparar o banco. Confira a configuração administrativa e execute o instalador novamente.");
            System.exit(1);
        }
    }

    /** Os arquivos já devem existir com ACL restrita; sobrescrever mantém essa ACL. */
    static Properties separarConfiguracoes(Path runtime, Path administrativo) throws IOException {
        if (Files.isSameFile(runtime, administrativo)) throw new IOException("Arquivos de configuração devem ser separados.");
        Properties antigo = DB.carregarArquivo(runtime);
        Properties admin = DB.carregarArquivo(administrativo);
        if (!admin.containsKey("migracao.user")) {
            for (String chave : new String[]{"dburl", "migracao.user", "migracao.password"}) {
                if (antigo.containsKey(chave)) admin.setProperty(chave, antigo.getProperty(chave));
            }
        }
        DB.validarConfiguracao(admin, true);
        Properties app = new Properties();
        for (String chave : new String[]{"dburl", "user", "password"}) {
            if (antigo.containsKey(chave)) app.setProperty(chave, antigo.getProperty(chave));
        }
        DB.validarConfiguracao(app, false);
        DB.validarUsuarioRuntime(app);
        if (!admin.getProperty("dburl").equals(app.getProperty("dburl"))) throw new IOException("Bancos diferentes na configuração.");
        gravar(administrativo, admin);
        gravar(runtime, app);
        return admin;
    }

    private static void gravar(Path arquivo, Properties props) throws IOException {
        // Nunca cria arquivos de segredos com permissões herdadas: o instalador os cria antes da escrita.
        try (OutputStream out = Files.newOutputStream(arquivo, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            props.store(out, "SistemaPDV - configuracao externa");
        }
    }

    static void atualizar(Properties props) {
        DB.validarConfiguracao(props, true);
        Flyway flyway = Flyway.configure().dataSource(PrazosBanco.url(props.getProperty("dburl"), true), props.getProperty("migracao.user"), props.getProperty("migracao.password"))
                .locations("classpath:db-migrations").baselineOnMigrate(true).baselineVersion("1")
                .baselineDescription("estrutura existente antes do Flyway").ignoreMigrationPatterns(new String[0]).load();
        if (java.util.Arrays.stream(flyway.info().all()).noneMatch(m -> m.getState().isResolved())) {
            throw new DbException("Os scripts de atualização não foram encontrados no pacote.");
        }
        flyway.migrate();
    }
}
