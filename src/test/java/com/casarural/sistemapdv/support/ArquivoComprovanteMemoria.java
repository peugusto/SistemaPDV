package com.casarural.sistemapdv.support;
import com.casarural.sistemapdv.model.dao.ArquivoComprovanteDao;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado.Tipo;
import java.util.*;
public class ArquivoComprovanteMemoria implements ArquivoComprovanteDao {
    public final Map<String,ComprovanteGuardado> documentos=new LinkedHashMap<>();
    public synchronized ComprovanteGuardado guardar(ComprovanteGuardado d) { return documentos.computeIfAbsent(d.getTipo()+":"+d.getReferencia(),k->d); }
    public synchronized Optional<ComprovanteGuardado> buscar(Tipo t,String r) { return Optional.ofNullable(documentos.get(t+":"+r)); }
    public synchronized Optional<ComprovanteGuardado> ultimaLista() {
        return documentos.values().stream().filter(d->d.getTipo()==Tipo.LISTA_COMPRAS).reduce((a,b)->b);
    }
}
