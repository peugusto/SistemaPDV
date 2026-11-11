package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.impl.CustomerDaoJdbc;
import com.casarural.sistemapdv.services.CustomerService;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.casarural.sistemapdv.controller.ProtecoesPinTelasTest.btn;
import static com.casarural.sistemapdv.controller.ProtecoesPinTelasTest.dialogo;
import static com.casarural.sistemapdv.controller.ProtecoesPinTelasTest.tela;
import static com.casarural.sistemapdv.controller.ProtecoesPinTelasTest.txt;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class RegisterCustomerControllerTest {
    @Test
    void falhaAoFecharConexaoPreservaSucessoEBloqueiaSegundoCadastro() throws Exception {
        JavafxTestSupport.executar(() -> {
            Banco banco = new Banco();
            banco.falharAoFechar = true;
            FXMLLoader tela = abrir(banco);
            RegisterCustomerController controller = tela.getController();

            controller.onBtnSaveAction();
            assertTrue(btn(tela, "btnSave").isDisabled());
            assertEquals("Cliente cadastrado com sucesso!", dialogo().getContentText());
            assertEquals(1, banco.commits);
            controller.onBtnSaveAction();

            assertEquals(1, banco.insercoes);
            assertEquals(1, banco.clientesPersistidos);
        });
    }

    @Test
    void respostaDoCommitPerdidaBloqueiaRepeticaoDoCadastro() throws Exception {
        JavafxTestSupport.executar(() -> {
            Banco banco = new Banco();
            banco.perderRespostaCommit = true;
            FXMLLoader tela = abrir(banco);
            RegisterCustomerController controller = tela.getController();

            controller.onBtnSaveAction();
            assertTrue(btn(tela, "btnSave").isDisabled());
            assertEquals("Fechar", btn(tela, "btnCancel").getText());
            assertEquals("O cadastro pode já estar salvo", dialogo().getHeaderText());
            controller.onBtnSaveAction();

            assertEquals(1, banco.insercoes);
            assertEquals(1, banco.clientesPersistidos);
        });
    }

    @Test
    void falhaAntesDoCommitDesfazInsercaoEPermiteNovaTentativa() throws Exception {
        JavafxTestSupport.executar(() -> {
            Banco banco = new Banco();
            banco.omitirChave = true;
            FXMLLoader tela = abrir(banco);
            RegisterCustomerController controller = tela.getController();

            controller.onBtnSaveAction();
            assertFalse(btn(tela, "btnSave").isDisabled());
            assertEquals(1, banco.rollbacks);
            assertEquals(0, banco.commits);
            assertEquals(0, banco.clientesPersistidos);

            banco.omitirChave = false;
            controller.onBtnSaveAction();
            assertEquals(2, banco.insercoes);
            assertEquals(1, banco.clientesPersistidos);
            assertTrue(btn(tela, "btnSave").isDisabled());
        });
    }

    private static FXMLLoader abrir(Banco banco) throws Exception {
        FXMLLoader loader = tela("RegisterCustomer");
        RegisterCustomerController controller = loader.getController();
        controller.setCustomerService(new CustomerService(new CustomerDaoJdbc(banco::conectar)));
        txt(loader, "txtNome").setText("Maria");
        return loader;
    }

    private static class Banco {
        private boolean falharAoFechar;
        private boolean perderRespostaCommit;
        private boolean omitirChave;
        private boolean autocommit = true;
        private int insercoes;
        private int pendentes;
        private int clientesPersistidos;
        private int commits;
        private int rollbacks;

        Connection conectar() {
            return proxy(Connection.class, (objeto, metodo, args) -> {
                switch (metodo.getName()) {
                    case "setAutoCommit":
                        autocommit = (boolean) args[0];
                        return null;
                    case "prepareStatement":
                        return statement();
                    case "commit":
                        commits++;
                        clientesPersistidos += pendentes;
                        pendentes = 0;
                        if (perderRespostaCommit) throw new SQLException("Resposta do commit perdida");
                        return null;
                    case "rollback":
                        rollbacks++;
                        pendentes = 0;
                        return null;
                    case "close":
                        if (falharAoFechar) throw new SQLException("Falha ao fechar a conexão");
                        return null;
                    default:
                        throw new UnsupportedOperationException(metodo.getName());
                }
            });
        }

        private PreparedStatement statement() {
            return proxy(PreparedStatement.class, (objeto, metodo, args) -> {
                switch (metodo.getName()) {
                    case "setString":
                    case "setBigDecimal":
                    case "close":
                        return null;
                    case "executeUpdate":
                        insercoes++;
                        if (autocommit) clientesPersistidos++;
                        else pendentes++;
                        return 1;
                    case "getGeneratedKeys":
                        return proxy(ResultSet.class, (resultado, operacao, parametros) -> {
                            switch (operacao.getName()) {
                                case "next": return !omitirChave;
                                case "getInt": return 17;
                                case "close": return null;
                                default: throw new UnsupportedOperationException(operacao.getName());
                            }
                        });
                    default:
                        throw new UnsupportedOperationException(metodo.getName());
                }
            });
        }

        private static <T> T proxy(Class<T> tipo, InvocationHandler handler) {
            return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler));
        }
    }
}
