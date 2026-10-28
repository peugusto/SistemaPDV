# Comprovante de venda — Elgin i7

Depois de salvar uma venda, o sistema gera um comprovante não fiscal com itens,
subtotal, desconto, total, formas de pagamento e troco. Trocas mostram os créditos
separados dos pagamentos novos e as devoluções ao cliente.

## Recebimento de fiado

O recebimento de fiado também emite um comprovante após a gravação. Mostra o nome
e telefone do cliente (quando informado), seguido pelos produtos das compras
abatidas, separados pela data. Cada produto mostra quantidade, nome e valor.
No final há um único resumo: total do fiado antes do pagamento, pago agora e
restante. Não repete valores ou situações por pedido. O total do fiado representa
a dívida da conta, considerando descontos e pagamentos anteriores.

O comprovante usa os dados capturados na própria transação do recebimento e o
mesmo formato de 70 x 40 mm. Falhas de impressão ou de atualização da tela não
desfazem nem repetem o pagamento. Não há alteração de estrutura do banco.

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
etiqueta com **70 mm de largura e 40 mm de altura**, sem rotação, no driver.
O sistema pede esse mesmo tamanho e margens de 2 mm; se o driver substituir o
formato por outro tamanho, o envio é recusado para evitar impressão deslocada.
Se a fila tiver outro nome,
inicie com `-Dpdv.impressora.nome="Nome exato da impressora"`.

O envio ocorre em segundo plano, após a confirmação da gravação. Se falhar, o
operador é avisado de que a venda já foi salva e não deve ser refeita. Não há
reenvio automático: a aceitação pelo spooler não comprova a saída física do papel.
Não alteramos o banco de dados para essa integração.

O layout usa 36 colunas, fonte em negrito e preto sólido. Solicita qualidade alta
quando o driver aceita. A intensidade térmica física da i7 é configurada pelas
microchaves do equipamento (manual Elgin, seção 9.2); não é alterada por este código.

Os 40 mm são a altura de cada etiqueta, não o diâmetro de uma bobina. Comprovantes
com muitas linhas continuam em outras etiquetas, sem cortar itens ou reduzir toda
a venda para uma letra minúscula.

Antes de usar na loja, testar com papel: acentos, margens de 70 x 40 mm, comprovante
longo, pagamentos divididos, desconto, dinheiro com troco, fiado e troca.
