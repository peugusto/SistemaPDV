package com.casarural.sistemapdv.util;

import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

/** Identifica o estado financeiro lido na abertura, sem coluna ou migração nova. */
public final class VersaoTroca {
    private final MessageDigest resumo;

    public VersaoTroca(VendaParaDesfazer v) {
        try {
            resumo = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        adicionar(v.getIdPedido());
        adicionar(v.getIdCliente());
        adicionar(v.getStatus());
        adicionar(v.getDataPedido());
        adicionar(Money.of(v.getValorTotal()).toPlainString());
        adicionar(Money.of(v.getDesconto()).toPlainString());
    }

    public void pagamento(int id, String metodo, BigDecimal valor, String origem,
                          LocalDateTime data, Integer caixa, Integer recebimento) {
        adicionar(id);
        adicionar(metodo);
        adicionar(Money.of(valor).toPlainString());
        adicionar(origem);
        adicionar(data);
        adicionar(caixa);
        adicionar(recebimento);
    }

    private void adicionar(Object v) {
        resumo.update((byte) (v == null ? 0 : 1));
        if (v == null) return;
        byte[] bytes = v.toString().getBytes(StandardCharsets.UTF_8);
        for (int deslocamento = 24; deslocamento >= 0; deslocamento -= 8) {
            resumo.update((byte) (bytes.length >>> deslocamento));
        }
        resumo.update(bytes);
    }

    public String concluir() { return Base64.getEncoder().encodeToString(resumo.digest()); }
}
