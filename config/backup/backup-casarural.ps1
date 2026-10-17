# =====================================================================================
#  Backup do banco do SistemaPDV (Casa Rural)
#
#  O que faz:
#   1. Copia o banco "casarural" inteiro com o mysqldump (sem parar o sistema).
#   2. Confere se a copia terminou inteira.
#   3. Compacta em .zip e guarda em PastaLocal.
#   4. Copia o .zip para PastaNuvem (pasta do Google Drive / OneDrive, se existir).
#   5. Apaga copias com mais de DiasGuardar dias.
#   6. Escreve tudo em backup.log (na PastaLocal).
#
#  Agendador de Tarefas do Windows (todo dia, depois de fechar a loja):
#   Programa:   powershell.exe
#   Argumentos: -NoProfile -ExecutionPolicy Bypass -File "C:\ProgramData\SistemaPDV\backup\backup-casarural.ps1"
#
#  A senha NAO fica aqui: fica em backup.cnf (veja backup.cnf.exemplo).
#  Texto sem acentos de proposito: o PowerShell do Windows estraga acentos em arquivo sem BOM.
# =====================================================================================

param(
    [string]$MysqlBin    = "C:\Program Files\MySQL\MySQL Server 8.0\bin",
    [string]$Banco       = "casarural",
    [string]$Credenciais = "C:\ProgramData\SistemaPDV\backup\backup.cnf",
    [string]$PastaLocal  = "C:\ProgramData\SistemaPDV\backup\copias",
    [string]$PastaNuvem  = "",          # ex.: "C:\Users\Loja\Google Drive\Backup Casa Rural" (vazio = sem nuvem)
    [int]   $DiasGuardar = 30
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $PastaLocal)) { New-Item -ItemType Directory -Force -Path $PastaLocal | Out-Null }
$log = Join-Path $PastaLocal "backup.log"

function Escrever([string]$texto) {
    $linha = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $texto
    Add-Content -Path $log -Value $linha -Encoding UTF8
    Write-Output $linha
}

try {
    $dump = Join-Path $MysqlBin "mysqldump.exe"
    if (-not (Test-Path $dump))        { throw "mysqldump nao encontrado em $MysqlBin" }
    if (-not (Test-Path $Credenciais)) { throw "Arquivo de credenciais nao encontrado: $Credenciais" }

    $nome = "casarural_{0}" -f (Get-Date -Format "yyyy-MM-dd_HHmm")
    $sql  = Join-Path $PastaLocal "$nome.sql"
    $zip  = Join-Path $PastaLocal "$nome.zip"

    # 1. Copia. --single-transaction: le tudo de uma vez sem travar o caixa (tabelas InnoDB).
    #    --result-file: o proprio mysqldump grava o arquivo (o ">" do PowerShell estragaria a codificacao).
    #    --defaults-extra-file precisa ser o PRIMEIRO argumento.
    & $dump "--defaults-extra-file=$Credenciais" `
        --single-transaction --no-tablespaces --routines --triggers `
        --default-character-set=utf8mb4 `
        "--result-file=$sql" $Banco 2>&1 | ForEach-Object { Escrever "mysqldump: $_" }
    if ($LASTEXITCODE -ne 0) { throw "mysqldump terminou com erro (codigo $LASTEXITCODE)" }

    # 2. Confere: a ultima linha de uma copia completa e "-- Dump completed ..."
    $fim = Get-Content -Path $sql -Tail 1
    if ($fim -notlike "-- Dump completed*") { throw "Copia incompleta (o arquivo nao termina com 'Dump completed')" }

    # 3. Compacta
    Compress-Archive -Path $sql -DestinationPath $zip -Force
    Remove-Item $sql
    $tamanho = "{0:N1} MB" -f ((Get-Item $zip).Length / 1MB)
    Escrever "OK: $zip ($tamanho)"

    # 4. Nuvem (se configurada e a pasta existir; o Google Drive/OneDrive sobe quando tiver internet)
    if ($PastaNuvem -ne "") {
        if (Test-Path $PastaNuvem) {
            Copy-Item $zip -Destination $PastaNuvem -Force
            Escrever "Copiado para a nuvem: $PastaNuvem"
        } else {
            Escrever "AVISO: pasta da nuvem nao encontrada: $PastaNuvem (copia ficou so no computador)"
        }
    }

    # 5. Apaga copias antigas (local e nuvem)
    $limite = (Get-Date).AddDays(-$DiasGuardar)
    foreach ($pasta in @($PastaLocal, $PastaNuvem)) {
        if ($pasta -ne "" -and (Test-Path $pasta)) {
            Get-ChildItem -Path $pasta -Filter "casarural_*.zip" |
                Where-Object { $_.LastWriteTime -lt $limite } |
                ForEach-Object { Remove-Item $_.FullName; Escrever "Apagada copia antiga: $($_.Name)" }
        }
    }
    exit 0
}
catch {
    Escrever "ERRO NO BACKUP: $($_.Exception.Message)"
    # deixa um arquivo bem visivel para quem olhar a pasta
    Set-Content -Path (Join-Path $PastaLocal "ULTIMO_BACKUP_FALHOU.txt") -Value ("{0} - {1}" -f (Get-Date), $_.Exception.Message)
    exit 1
}
