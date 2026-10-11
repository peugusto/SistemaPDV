package com.casarural.sistemapdv.util;

/**
 * Telefone brasileiro: guarda só os números (DDD + número) e mostra formatado.
 *   "(11) 98765-4321", "11 98765 4321", "11987654321" -> guarda "11987654321"
 *   "11987654321" -> mostra "(11) 98765-4321";  "1133334444" -> "(11) 3333-4444"
 */
public final class Telefone {

    private Telefone() {}

    /**
     * Só os números, ou null se o campo estiver vazio (o telefone é opcional).
     * Lança IllegalArgumentException, com mensagem para o usuário, se não tiver DDD + número.
     */
    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String digitos = texto.replaceAll("\\D", "");
        if (digitos.length() != 10 && digitos.length() != 11) {
            throw new IllegalArgumentException(
                    "Telefone incompleto. Digite o DDD e o número, por exemplo (11) 98765-4321.");
        }
        return digitos;
    }

    /** Formata para exibir; devolve "" se não houver telefone. */
    public static String formatar(String digitos) {
        if (digitos == null || digitos.isBlank()) return "";
        if (digitos.length() == 11) {
            return String.format("(%s) %s-%s", digitos.substring(0, 2), digitos.substring(2, 7), digitos.substring(7));
        }
        if (digitos.length() == 10) {
            return String.format("(%s) %s-%s", digitos.substring(0, 2), digitos.substring(2, 6), digitos.substring(6));
        }
        return digitos;
    }
}
