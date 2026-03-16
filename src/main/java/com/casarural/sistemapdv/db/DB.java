package com.casarural.sistemapdv.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DB {

    public static Connection conn = null;


    public static Connection getConnection() {
        if (conn == null) {
            try {
                Properties props = loadProperties();
                String url = props.getProperty("dburl");
                conn = DriverManager.getConnection(url, props);

            }catch(SQLException e) {
                e.printStackTrace();
            }

        }
        return conn;

    }

    private static Properties loadProperties() {
        try (InputStream is = DB.class.getClassLoader().getResourceAsStream("db.properties")){
            Properties props = new Properties();
            props.load(is);
            return props;

        }catch(IOException e) {
            throw new DbException(e.getMessage());
        }
    }


    public static void closeConnection() {
        if (conn != null) {
            try {
                conn.close();
            }catch(SQLException e) {
                throw new DbException("Erro ao fechar a conexão.");
            }
        }
    }

    public static void closeStatement (Statement st) {
        if (st != null) {
            try {
                st.close();
            }catch(SQLException e) {
                throw new DbException("Erro ao fechar o statement.");
            }
        }
    }

}
