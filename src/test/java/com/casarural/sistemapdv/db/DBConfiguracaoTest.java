package com.casarural.sistemapdv.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Apenas propriedades fictícias; a validação não abre conexão. */
class DBConfiguracaoTest {
    static Properties valida() {
        Properties p=new Properties(); p.setProperty("dburl","jdbc:mysql://127.0.0.1:1/TESTE_INEXISTENTE");
        p.setProperty("user","TESTE"); p.setProperty("password","SEGREDO_FICTICIO");
        p.setProperty("migracao.user","TESTE_MIGRACAO"); p.setProperty("migracao.password","SEGREDO_MIGRACAO"); return p;
    }
    @Test void urlAusenteVaziaOuInvalidaGeraErroOrientativoSemVazarValor() {
        for(String url:new String[]{"", "  ","http://SEGREDO_FICTICIO", "jdbc:mysql://127.0.0.1:porta/TESTE", "jdbc:mysql://127.0.0.1:1/TESTE?connectTimeout=invalido"}) {
            Properties p=valida(); p.setProperty("dburl",url);
            DbException e=assertThrows(DbException.class,()->DB.validarConfiguracao(p,true));
            assertTrue(e.getMessage().contains("dburl")); assertFalse(e.getMessage().contains("SEGREDO_FICTICIO")); assertNull(e.getCause());
        }
        Properties p=valida(); p.remove("dburl"); assertThrows(DbException.class,()->DB.validarConfiguracao(p,true));
    }
    @Test void validaCamposObrigatoriosDeExecucaoEMigracao() {
        for(String campo:new String[]{"user","password","migracao.user","migracao.password"}) {
            Properties p=valida(); p.remove(campo);
            DbException e=assertThrows(DbException.class,()->DB.validarConfiguracao(p,campo.startsWith("migracao")));
            assertTrue(e.getMessage().contains(campo)); assertFalse(e.getMessage().contains("SEGREDO"));
        }
        Properties p=valida(); p.setProperty("migracao.password"," "); assertThrows(DbException.class,()->DB.validarConfiguracao(p,true));
    }
    @Test void configuracaoValidaNaoExigeServidorESenhaVaziaDeExecucaoEPermitida() {
        Properties p=valida(); p.setProperty("password",""); p.setProperty("dburl","  "+p.getProperty("dburl")+"  ");
        assertDoesNotThrow(()->DB.validarConfiguracao(p,false)); assertDoesNotThrow(()->DB.validarConfiguracao(p,true));
        assertTrue(p.getProperty("dburl").startsWith("jdbc:mysql:"));
    }
    @Test void migracaoComArquivoSemUrlFalhaAntesDeFlyway(@TempDir Path tmp) throws Exception {
        Path config=tmp.resolve("config-ficticia.properties"); Files.writeString(config,"migracao.user=TESTE\nmigracao.password=SEGREDO_FICTICIO\n");
        String anterior=System.getProperty("pdv.config"); System.setProperty("pdv.config",config.toString());
        try { DbException e=assertThrows(DbException.class,DB::migrate); assertTrue(e.getMessage().contains("dburl")); }
        finally { if(anterior==null) System.clearProperty("pdv.config"); else System.setProperty("pdv.config",anterior); }
    }
}
