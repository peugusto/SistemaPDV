package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Telefone;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

public class RegisterCustomerController implements Initializable {

    private CustomerService service;
    private Customer entity;
    private ProtecaoPin protecao = new ProtecaoPin();
    private boolean salvando;
    private boolean salvamentoBloqueado;

    @FXML private TextField txtNome;
    @FXML private TextField txtPhoneNumber;
    @FXML private TextField txtLimite;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    public void setCustomerService(CustomerService service) {
        this.service = service;
    }

    public void setCustomer(Customer entity) {
        this.entity = entity;
    }

    @FXML
    public void onBtnSaveAction() {
        if (salvando || salvamentoBloqueado) return;
        if (service == null) {
            throw new IllegalStateException("Service estava nulo");
        }
        salvando = true;
        btnSave.setDisable(true);
        try {
            Customer obj = getFormData();
            if (obj.getLimiteCredito().signum()>0 && !protecao.autorizar(btnSave.getScene().getWindow(),
                    "conceder limite inicial de "+Money.format(obj.getLimiteCredito())+" a "+obj.getNomeCliente())) return;
            service.insert(obj);
            entity = obj;
            salvamentoBloqueado = true;
        } catch (ResultadoTransacaoIncertoException e) {
            salvamentoBloqueado = true;
            btnCancel.setText("Fechar");
            Alerts.showAlert("Confirmação pendente", "O cadastro pode já estar salvo",
                    "Feche esta janela e procure o cliente na lista antes de cadastrar novamente.",
                    AlertType.WARNING);
            return;
        } catch (IllegalArgumentException e) {
            Alerts.showAlert("Erro de Validação", null, e.getMessage(), AlertType.ERROR);
            return;
        } catch (DbException | SQLException e) {
            Alerts.showAlert("Erro no Banco", "Não foi possível salvar", e.getMessage(), AlertType.ERROR);
            return;
        } finally {
            salvando = false;
            btnSave.setDisable(salvamentoBloqueado);
        }
        Alerts.showAlert("Sucesso", null, "Cliente cadastrado com sucesso!", AlertType.INFORMATION);
        closeStage();
    }

    private Customer getFormData() {
        if (txtNome.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do cliente é obrigatório.");
        }

        Customer novo = new Customer();
        novo.setNomeCliente(txtNome.getText());
        novo.setTelefone(Telefone.normalizar(txtPhoneNumber.getText()));

        String limiteStr = txtLimite.getText();
        novo.setLimiteCredito(limiteStr.trim().isEmpty() ? Money.ZERO : Money.parse(limiteStr));
        if(novo.getLimiteCredito().signum()<0)throw new IllegalArgumentException("O limite de crédito não pode ser negativo.");
        return novo;
    }

    @FXML
    public void onBtnCancelAction() {
        if (salvando) return;
        if (salvamentoBloqueado) {
            closeStage();
            return;
        }
        if (Alerts.showConfirmation("Sair", "Deseja voltar ao menu?", "Dados não salvos serão perdidos.")) {
            closeStage();
        }
    }

    private void closeStage() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Constraints.setTextFieldMaxLength(txtNome, Customer.TAMANHO_MAX_NOME);
        Constraints.setTextFieldMoney(txtLimite);
        Constraints.setTextFieldTelefone(txtPhoneNumber);

    }
}
