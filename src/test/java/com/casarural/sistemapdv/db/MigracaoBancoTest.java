package com.casarural.sistemapdv.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class MigracaoBancoTest {
    static final String URL="jdbc:mysql://127.0.0.1:1/SINTETICO";
    static String legado() {
        return "dburl="+URL+"\nuser=pdv_app\npassword=APP_FICTICIO\\\\barra\nmigracao.user=pdv_migracao\nmigracao.password=ADMIN_FICTICIO\\nlinha\\u00e3\n";
    }
    @Test void separaLegadoMantendoEscapesSemCopiarSegredoParaRuntime(@TempDir Path dir) throws Exception {
        Path app=dir.resolve("app.properties"),admin=dir.resolve("admin.properties");
        Files.writeString(app,legado());Files.writeString(admin,"");
        MigracaoBanco.separarConfiguracoes(app,admin);
        Properties a=DB.carregarArquivo(app),m=DB.carregarArquivo(admin);
        assertEquals(3,a.size());assertEquals("APP_FICTICIO\\barra",a.getProperty("password"));
        assertFalse(Files.readString(app).contains("ADMIN_FICTICIO"));assertFalse(a.containsKey("migracao.user"));
        assertEquals("ADMIN_FICTICIO\nlinhaã",m.getProperty("migracao.password"));
        assertDoesNotThrow(()->DB.validarUsuarioRuntime(a));
        MigracaoBanco.separarConfiguracoes(app,admin);
        assertEquals(m,DB.carregarArquivo(admin));assertEquals(a,DB.carregarArquivo(app));
    }
    @Test void configuracaoInvalidaNaoTruncaArquivos(@TempDir Path dir) throws Exception {
        Path app=dir.resolve("app.properties"),admin=dir.resolve("admin.properties");
        Files.writeString(app,legado().replace("migracao.user=pdv_migracao\n",""));Files.writeString(admin,"");
        String antes=Files.readString(app);
        assertThrows(DbException.class,()->MigracaoBanco.separarConfiguracoes(app,admin));
        assertEquals(antes,Files.readString(app));assertEquals("",Files.readString(admin));
    }
    @Test void exigeArquivosDistintosEPreCriados(@TempDir Path dir) throws Exception {
        Path app=dir.resolve("app.properties"),admin=dir.resolve("admin.properties");Files.writeString(app,legado());
        assertThrows(java.io.IOException.class,()->MigracaoBanco.separarConfiguracoes(app,app));
        assertThrows(java.io.IOException.class,()->MigracaoBanco.separarConfiguracoes(app,admin));assertFalse(Files.exists(admin));
    }
    @Test void runtimeRecusaCredencialAdministrativaOuFormatoLegado() {
        for(String usuario:new String[]{"root","ROOT","pdv_migracao"}) {
            Properties p=new Properties();p.setProperty("user",usuario);assertThrows(DbException.class,()->DB.validarUsuarioRuntime(p));
        }
        Properties p=new Properties();p.setProperty("user","pdv_app");p.setProperty("migracao.password","FICTICIO");
        DbException e=assertThrows(DbException.class,()->DB.validarUsuarioRuntime(p));
        assertTrue(e.getMessage().contains("instalador"));assertFalse(e.getMessage().contains("FICTICIO"));
    }
    @Test void bancosDiferentesNaoSaoAtualizadosPorEngano(@TempDir Path dir) throws Exception {
        Path app=dir.resolve("app.properties"),admin=dir.resolve("admin.properties");Files.writeString(app,legado());
        String adm="dburl="+URL+"_OUTRO\nmigracao.user=pdv_migracao\nmigracao.password=FICTICIO\n";Files.writeString(admin,adm);
        assertThrows(java.io.IOException.class,()->MigracaoBanco.separarConfiguracoes(app,admin));
        assertEquals(legado(),Files.readString(app));assertEquals(adm,Files.readString(admin));
    }
}
