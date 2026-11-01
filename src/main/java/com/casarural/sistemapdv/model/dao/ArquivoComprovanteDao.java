package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.ComprovanteGuardado;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado.Tipo;
import java.util.Optional;

public interface ArquivoComprovanteDao {
    /** Nunca substitui o conteúdo de uma origem que já foi guardada. */
    ComprovanteGuardado guardar(ComprovanteGuardado documento);
    Optional<ComprovanteGuardado> buscar(Tipo tipo, String referencia);
    Optional<ComprovanteGuardado> ultimaLista();
}
