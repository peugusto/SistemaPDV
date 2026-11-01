package com.casarural.sistemapdv.support;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import com.casarural.sistemapdv.model.dao.RelatorioImpressaoDao;
import java.util.concurrent.Executor;
/** Destinos falsos continuam isolados do arquivo JDBC da produção. */
public class ImpressaoTeste extends ImpressaoVendaService {
    public ImpressaoTeste(Destino d,Executor e) { this(d,e,null); }
    public ImpressaoTeste(Destino d,Executor e,RelatorioImpressaoDao relatorios) { super(d,e,relatorios,new ArquivoComprovanteMemoria()); }
}
