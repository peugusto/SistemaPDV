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
#  O instalador do SistemaPDV ja copia este arquivo, cria o backup.cnf e agenda a tarefa
#  "SistemaPDV - Backup" (12:00 e 18:30). A pasta da nuvem fica no nuvem.txt, ao lado do backup.cnf.
#  Sem o instalador, agende a mao (Agendador de Tarefas do Windows):
#   Programa:   powershell.exe
#   Argumentos: -NoProfile -ExecutionPolicy Bypass -File "C:\ProgramData\SistemaPDV\backup\backup-casarural.ps1"
#
#  A senha NAO fica aqui: fica em backup.cnf (veja backup.cnf.exemplo).
#  Texto sem acentos de proposito: o PowerShell do Windows estraga acentos em arquivo sem BOM.
# =====================================================================================

param(
    [string]$MysqlBin    = "",          # vazio = o MySQL do instalador do SistemaPDV ou um MySQL Server instalado
    [string]$Banco       = "casarural",
    [string]$Credenciais = "C:\ProgramData\SistemaPDV\backup\backup.cnf",
    [string]$PastaLocal  = "C:\ProgramData\SistemaPDV\backup\copias",
    [string]$PastaNuvem  = "",          # ex.: "C:\Users\Loja\OneDrive\Backup Casa Rural" (vazio = le o nuvem.txt)
    [int]   $DiasGuardar = 30
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $PastaLocal)) { New-Item -ItemType Directory -Force -Path $PastaLocal | Out-Null }
$log = Join-Path $PastaLocal "backup.log"

if ($MysqlBin -eq "") {
    $candidatos = @("C:\Program Files\SistemaPDV\mysql\bin",
                    "C:\Program Files\MySQL\MySQL Server 8.4\bin",
                    "C:\Program Files\MySQL\MySQL Server 8.0\bin")
    $MysqlBin = $candidatos | Where-Object { Test-Path (Join-Path $_ "mysqldump.exe") } | Select-Object -First 1
    if (-not $MysqlBin) { $MysqlBin = $candidatos[0] }
}

# Pasta da nuvem: se nao veio no comando, a primeira linha sem # do nuvem.txt (ao lado do backup.cnf).
# Assim da para mudar a pasta sem mexer na tarefa agendada.
if ($PastaNuvem -eq "") {
    $arquivoNuvem = Join-Path (Split-Path $Credenciais -Parent) "nuvem.txt"
    if (Test-Path $arquivoNuvem) {
        $linha = Get-Content $arquivoNuvem | Where-Object { $_.Trim() -ne "" -and -not $_.TrimStart().StartsWith("#") } | Select-Object -First 1
        if ($linha) { $PastaNuvem = $linha.Trim().Trim('"') }
    }
}

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
