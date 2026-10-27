# Comprovante de venda — Elgin i7

Depois de salvar uma venda, o sistema gera um comprovante não fiscal com itens,
subtotal, desconto, total, formas de pagamento e troco. Trocas mostram os créditos
separados dos pagamentos novos e as devoluções ao cliente.

## Nesta sessão, sem papel

Feche a execução anterior do PDV e, na raiz do projeto, execute:

```powershell
.\config\impressao\visualizar-comprovantes.ps1
```

Essa execução mostra cada comprovante no próprio terminal. Ela não consulta nem
envia trabalhos à impressora, não abre aplicativos e não grava arquivos de prévia.
O script restaura a variável de ambiente ao terminar; ele não altera configurações
do Windows nem o instalador.

Para executar pelo IntelliJ, use temporariamente a opção de VM
`-Dpdv.impressao.modo=previa` na configuração de execução. Remova-a ao encerrar os
testes. Só alterar o código não muda o modo de um processo que já está rodando:
reinicie a execução com essa opção antes de testar vendas sem papel.

## Produção

Sem o modo de prévia, o destino é a fila **ELGIN i7(USB)** do Windows. Configure a
bobina de 80 mm no driver. Se a fila tiver outro nome,
inicie com `-Dpdv.impressora.nome="Nome exato da impressora"`.

O envio ocorre em segundo plano, após a confirmação da gravação. Se falhar, o
operador é avisado de que a venda já foi salva e não deve ser refeita. Não há
reenvio automático: a aceitação pelo spooler não comprova a saída física do papel.
Não alteramos o banco de dados para essa integração.

Antes de usar na loja, testar com papel: acentos, margens de 80 mm, comprovante
longo, pagamentos divididos, desconto, dinheiro com troco, fiado e troca.
