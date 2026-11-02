package com.casarural.sistemapdv.db;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/** Limites efetivos, inclusive para instalações antigas com timeout zero na URL. */
public final class PrazosBanco {
    public static final int CONEXAO_MS = 3_000, REDE_MS = 10_000, CONSULTA_SEGUNDOS = 8;
    private static final Set<String> CONTROLADAS = Set.of("connecttimeout", "sockettimeout", "enablequerytimeouts",
            "querytimeoutkillsconnection", "autoreconnect", "autoreconnectforpools", "reconnectattxend");
    private PrazosBanco() {}

    public static String url(String original, boolean administrativa) {
        int inicio = original.indexOf('?');
        String base = inicio < 0 ? original : original.substring(0, inicio);
        List<String> parametros = new ArrayList<>();
        if (inicio >= 0) for (String parte : original.substring(inicio + 1).split("&")) {
            String chave = parte.split("=", 2)[0];
            if (!parte.isEmpty() && !CONTROLADAS.contains(URLDecoder.decode(chave, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))) parametros.add(parte);
        }
        parametros.add("connectTimeout=" + CONEXAO_MS);
        parametros.add("socketTimeout=" + (administrativa ? 60_000 : REDE_MS));
        parametros.add("enableQueryTimeouts=true");
        parametros.add("queryTimeoutKillsConnection=true");
        parametros.add("autoReconnect=false");
        parametros.add("autoReconnectForPools=false");
        parametros.add("reconnectAtTxEnd=false");
        return base + "?" + String.join("&", parametros);
    }

    public static Connection limitar(Connection original) throws SQLException {
        try {
            original.setNetworkTimeout(Runnable::run, REDE_MS);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a) -> {
                try {
                    Object retorno = m.invoke(original, a);
                    if (retorno instanceof Statement) {
                        Statement st = (Statement) retorno;
                        try { st.setQueryTimeout(CONSULTA_SEGUNDOS); }
                        catch (SQLException | RuntimeException e) { try { st.close(); } catch (SQLException limpeza) { e.addSuppressed(limpeza); } throw e; }
                    }
                    return retorno;
                } catch (InvocationTargetException e) { throw e.getCause(); }
            });
        } catch (SQLException | RuntimeException e) {
            try { original.close(); } catch (SQLException limpeza) { e.addSuppressed(limpeza); }
            throw e;
        }
    }
}
