package com.casarural.sistemapdv.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PIN dos relatórios. O PIN nunca é guardado como foi digitado: guarda-se um "hash" PBKDF2 com sal
 * aleatório. Quem abrir a tabela configuracao no Workbench vê só algo como
 * "pbkdf2$120000$sal$hash" e não consegue voltar para o PIN.
 *
 * - Sal: bytes aleatórios diferentes a cada PIN; dois PINs iguais geram hashes diferentes.
 * - Iterações: o cálculo é repetido muitas vezes de propósito, para deixar tentativas em massa lentas.
 */
public final class SenhaPin {

    public static final int TAMANHO_MINIMO = 4;
    public static final int TAMANHO_MAXIMO = 6;

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACOES = 120_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SenhaPin() {}

    /** Só números, de 4 a 6 dígitos. */
    public static boolean formatoValido(String pin) {
        return pin != null && pin.matches("\\d{" + TAMANHO_MINIMO + "," + TAMANHO_MAXIMO + "}");
    }

    public static String gerarHash(String pin) {
        if (!formatoValido(pin)) throw new IllegalArgumentException("O PIN deve ter de 4 a 6 números.");
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = calcular(pin, sal, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERACOES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    public static boolean confere(String pin, String guardado) {
        if (pin == null || guardado == null) return false;
        String[] partes = guardado.split("\\$");
        if (partes.length != 4 || !"pbkdf2".equals(partes[0])) return false;
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            // Comparação em tempo constante: não revela quantos bytes acertou
            return MessageDigest.isEqual(esperado, calcular(pin, sal, iteracoes));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] calcular(String pin, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), sal, iteracoes, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritmo de PIN indisponível", e);
        } finally {
            spec.clearPassword();
        }
    }
}
