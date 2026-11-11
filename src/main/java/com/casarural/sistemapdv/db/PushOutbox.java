package com.casarural.sistemapdv.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Eventos no mesmo commit financeiro. Nenhuma chamada HTTP e nenhuma credencial adicional. */
public final class PushOutbox {
    private PushOutbox() {}
    private static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("pdv.push.events.enabled",
                System.getenv().getOrDefault("PDV_PUSH_EVENTS_ENABLED", "false")));
    }
    public static void recordSale(Connection connection,int orderId) throws SQLException {
        if(!enabled()) return;
        requireTransaction(connection,orderId);
        try(PreparedStatement statement=connection.prepareStatement(
            "INSERT INTO push_event(id,source_key,kind,created_at) " +
            "SELECT UUID(),?,'sale',UTC_TIMESTAMP(6) FROM pedido WHERE id_pedido=? AND status IN ('PAGO','FIADO') " +
            "ON DUPLICATE KEY UPDATE id=push_event.id")) {
            statement.setString(1,"sale:"+orderId); statement.setInt(2,orderId); statement.executeUpdate();
        }
    }
    public static void recordReceipt(Connection connection,int receiptId) throws SQLException {
        if(!enabled()) return;
        requireTransaction(connection,receiptId);
        try(PreparedStatement statement=connection.prepareStatement(
            "INSERT INTO push_event(id,source_key,kind,created_at) " +
            "SELECT UUID(),?,'receipt',UTC_TIMESTAMP(6) FROM " +
            "(SELECT id_recebimento FROM pagamento WHERE id_recebimento=? AND origem='RECEBIMENTO_FIADO' AND valor_pago>0 LIMIT 1) r " +
            "ON DUPLICATE KEY UPDATE id=push_event.id")) {
            statement.setString(1,"receipt:"+receiptId); statement.setInt(2,receiptId); statement.executeUpdate();
        }
    }
    private static void requireTransaction(Connection connection,int id) throws SQLException {
        if(id<=0 || connection.getAutoCommit()) throw new SQLException("Evento de push deve acompanhar uma transacao financeira valida.");
    }
}
