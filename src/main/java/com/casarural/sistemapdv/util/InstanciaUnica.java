package com.casarural.sistemapdv.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Impede abrir o sistema duas vezes no mesmo usuário do Windows (duplo clique repetido no ícone).
 * Usa uma trava de arquivo na pasta temporária: o Windows solta a trava sozinho quando o programa
 * fecha, mesmo se fechar à força ou o PC desligar.
 */
public final class InstanciaUnica {

    private static final Logger log = LoggerFactory.getLogger(InstanciaUnica.class);

    // Guardados para a trava durar enquanto o programa estiver aberto
    private static FileChannel canal;
    private static FileLock trava;

    private InstanciaUnica() {}

    /** true se este é o único sistema aberto; false se já existe outro. */
    public static synchronized boolean obter() {
        Path arquivo = Paths.get(System.getProperty("java.io.tmpdir"), "sistemapdv-casarural.lock");
        try {
            canal = FileChannel.open(arquivo, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            trava = canal.tryLock();
            if (trava == null) {
                canal.close();
                return false;
            }
            return true;
        } catch (OverlappingFileLockException e) {
            return false;   // este mesmo programa já tem a trava
        } catch (IOException e) {
            // Sem conseguir criar a trava, não impede de abrir: melhor abrir duas vezes do que não abrir
            log.warn("Não foi possível verificar se o sistema já está aberto", e);
            return true;
        }
    }
}
