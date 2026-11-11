# =====================================================================================
#  Backup do banco do SistemaPDV (Casa Rural)
#
#  O que faz:
#   1. Copia o banco "casarural" inteiro com o mysqldump (sem parar o sistema).
#   2. Confere se a copia terminou inteira.
#   3. Compacta em .zip e guarda em PastaLocal.
#   4. Envia uma copia cifrada para PastaNuvem quando nuvem.cer estiver configurado.
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

$pastaConfig = Split-Path -Parent ([IO.Path]::GetFullPath($Credenciais))
$arquivoSeguranca = Join-Path $PSScriptRoot 'seguranca-config.ps1'
if (-not (Test-Path -LiteralPath $arquivoSeguranca -PathType Leaf)) {
    $arquivoSeguranca = Join-Path $PSScriptRoot '..\..\instalador\scripts\seguranca-config.ps1'
}
. $arquivoSeguranca

$executor = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$PastaLocal = [IO.Path]::GetFullPath($PastaLocal)
$log = Join-Path $PastaLocal 'backup.log'
$falhaAnterior = Join-Path $PastaLocal 'ULTIMO_BACKUP_FALHOU.txt'

if ($MysqlBin -eq "") {
    $candidatos = @("C:\Program Files\SistemaPDV\mysql\bin",
                    "C:\Program Files\MySQL\MySQL Server 8.4\bin",
                    "C:\Program Files\MySQL\MySQL Server 8.0\bin")
    $MysqlBin = $candidatos | Where-Object { Test-Path (Join-Path $_ "mysqldump.exe") } | Select-Object -First 1
    if (-not $MysqlBin) { $MysqlBin = $candidatos[0] }
}

function Escrever([string]$texto) {
    $linha = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $texto
    Add-Content -LiteralPath $log -Value $linha -Encoding UTF8
    Write-Output $linha
}

# ProcessStartInfo evita que stderr vire NativeCommandError no Windows PowerShell 5.1.
function ArgumentoWindows([string]$valor) {
    $valor = [regex]::Replace($valor, '(\\*)"', '${1}${1}\"')
    $valor = [regex]::Replace($valor, '(\\+)$', '${1}${1}')
    return '"' + $valor + '"'
}
function ExecutarNativo([string]$executavel, [string[]]$argumentos) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $executavel
    $info.Arguments = (($argumentos | ForEach-Object { ArgumentoWindows $_ }) -join ' ')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $processo = New-Object System.Diagnostics.Process
    $processo.StartInfo = $info
    try {
        $null = $processo.Start()
        # Drenar os dois canais em paralelo evita encher um pipe enquanto espera pelo outro.
        $saida = $processo.StandardOutput.ReadToEndAsync()
        $erros = $processo.StandardError.ReadToEndAsync()
        $processo.WaitForExit()
        $codigo = $processo.ExitCode
        foreach ($texto in @($saida.GetAwaiter().GetResult(), $erros.GetAwaiter().GetResult())) {
            foreach ($linha in ($texto -split '\r?\n')) {
                if ($linha -ne '') { Escrever "mysqldump: $linha" }
            }
        }
        if ($codigo -ne 0) { throw "mysqldump terminou com erro (codigo $codigo)" }
    }
    finally { $processo.Dispose() }
}

function Exportar-BackupCifrado([string]$Zip, [string]$Pasta, [string]$Certificado) {
    Confirmar-CaminhoPdv $Certificado
    if (-not (Test-Path -LiteralPath $Certificado -PathType Leaf)) {
        throw 'ZIP local salvo. Configure o certificado publico nuvem.cer para enviar uma copia externa.'
    }

    Add-Type -AssemblyName System.Security
    $dadosCertificado = [IO.File]::ReadAllBytes($Certificado)
    if ([Security.Cryptography.X509Certificates.X509Certificate2]::GetCertContentType($dadosCertificado) -ne 'Cert') {
        throw 'nuvem.cer deve conter apenas o certificado publico exportado em formato CER.'
    }
    $cert = [Security.Cryptography.X509Certificates.X509Certificate2]::new($dadosCertificado)
    try {
        if ($cert.GetKeyAlgorithm() -ne '1.2.840.113549.1.1.1' -or $cert.PublicKey.Key.KeySize -lt 2048) {
            throw 'O certificado de exportacao deve ter chave RSA de pelo menos 2048 bits.'
        }
        $conteudo = [Security.Cryptography.Pkcs.ContentInfo]::new([IO.File]::ReadAllBytes($Zip))
        $aes256 = [Security.Cryptography.Pkcs.AlgorithmIdentifier]::new(
            [Security.Cryptography.Oid]::new('2.16.840.1.101.3.4.1.42'))
        $cms = [Security.Cryptography.Pkcs.EnvelopedCms]::new($conteudo, $aes256)
        $destinatario = [Security.Cryptography.Pkcs.CmsRecipient]::new($cert)
        $cms.Encrypt($destinatario)

        $destino = Join-Path $Pasta ([IO.Path]::GetFileName($Zip) + '.cms')
        $temporario = "$destino.pendente"
        $criado = $false
        try {
            $stream = [IO.File]::Open($temporario, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
            $criado = $true
            try {
                $cifrado = $cms.Encode()
                $stream.Write($cifrado, 0, $cifrado.Length)
            } finally {
                $stream.Dispose()
            }
            [IO.File]::Move($temporario, $destino)
        } finally {
            if ($criado -and (Test-Path -LiteralPath $temporario)) {
                Remove-Item -LiteralPath $temporario -ErrorAction SilentlyContinue
            }
        }
        Escrever "Copia cifrada enviada para $destino"
    } finally {
        $cert.Dispose()
    }
}

$pastaProtegida = $false
$sql = $null
$zipTemporario = $null
$codigoSaida = 0
try {
    Proteger-CopiasBackupPdv $PastaLocal -Proprietario $executor
    Preparar-ArquivoPdv $log -Proprietario $executor
    $pastaProtegida = $true

    if ($PastaNuvem -eq '') {
        $arquivoNuvem = Join-Path $pastaConfig 'nuvem.txt'
        if (Test-Path -LiteralPath $arquivoNuvem -PathType Leaf) {
            $linha = Get-Content -LiteralPath $arquivoNuvem |
                Where-Object { $_.Trim() -ne '' -and -not $_.TrimStart().StartsWith('#') } | Select-Object -First 1
            if ($linha) { $PastaNuvem = $linha.Trim().Trim('"') }
        }
    }

    $dump = Join-Path $MysqlBin "mysqldump.exe"
    if (-not (Test-Path -LiteralPath $dump -PathType Leaf)) { throw "mysqldump nao encontrado em $MysqlBin" }
    if (-not (Test-Path -LiteralPath $Credenciais -PathType Leaf)) { throw "Arquivo de credenciais nao encontrado: $Credenciais" }

    $nome = 'casarural_{0}_{1}' -f (Get-Date -Format 'yyyy-MM-dd_HHmmss'), [Guid]::NewGuid().ToString('N')
    $sql  = Join-Path $PastaLocal "$nome.sql"
    $zip  = Join-Path $PastaLocal "$nome.zip"
    $zipTemporario = Join-Path $PastaLocal "$nome.pendente.zip"
    Preparar-ArquivoPdv $sql -Proprietario $executor

    # 1. Copia. --single-transaction: le tudo de uma vez sem travar o caixa (tabelas InnoDB).
    #    --result-file: o proprio mysqldump grava o arquivo (o ">" do PowerShell estragaria a codificacao).
    #    --defaults-extra-file precisa ser o PRIMEIRO argumento.
    ExecutarNativo $dump @(
        "--defaults-extra-file=$Credenciais",
        '--single-transaction', '--no-tablespaces', '--routines', '--triggers',
        '--default-character-set=utf8mb4', "--result-file=$sql", $Banco
    )
    # 2. Confere: a ultima linha de uma copia completa e "-- Dump completed ..."
    $fim = Get-Content -LiteralPath $sql -Tail 1
    if ($fim -notlike "-- Dump completed*") { throw "Copia incompleta (o arquivo nao termina com 'Dump completed')" }

    # 3. Compacta
    Compress-Archive -LiteralPath $sql -DestinationPath $zipTemporario
    Preparar-ArquivoPdv $zipTemporario -Proprietario $executor
    [IO.File]::Move($zipTemporario, $zip)
    Remove-Item -LiteralPath $sql
    $tamanho = "{0:N1} MB" -f ((Get-Item -LiteralPath $zip).Length / 1MB)
    Escrever "OK: $zip ($tamanho)"

    # A retencao local continua valendo se o envio externo falhar.
    $limite = (Get-Date).AddDays(-$DiasGuardar)
    Get-ChildItem -LiteralPath $PastaLocal -File -Filter 'casarural_*.zip' |
        Where-Object { $_.FullName -ne $zip -and $_.LastWriteTime -lt $limite -and $_.Name -notlike '*.pendente.zip' } |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName; Escrever "Apagada copia antiga: $($_.Name)" }

    # 4. Nuvem (se configurada e a pasta existir; o Google Drive/OneDrive sobe quando tiver internet)
    if ($PastaNuvem -ne "") {
        if (Test-Path -LiteralPath $PastaNuvem -PathType Container) {
            $PastaNuvem = (Resolve-Path -LiteralPath $PastaNuvem).ProviderPath
            Exportar-BackupCifrado $zip $PastaNuvem (Join-Path $pastaConfig 'nuvem.cer')
            Get-ChildItem -LiteralPath $PastaNuvem -File -Filter 'casarural_*.zip.cms' |
                Where-Object { $_.LastWriteTime -lt $limite } |
                ForEach-Object { Remove-Item -LiteralPath $_.FullName; Escrever "Apagada copia cifrada antiga: $($_.Name)" }
        } else {
            Escrever "AVISO: pasta da nuvem nao encontrada: $PastaNuvem (copia ficou so no computador)"
        }
    }

    # So limpa o alarme antigo depois de concluir todas as etapas desta tentativa.
    if (Test-Path -LiteralPath $falhaAnterior) { Remove-Item -LiteralPath $falhaAnterior }
    Escrever "BACKUP CONCLUIDO COM SUCESSO"
}
catch {
    $codigoSaida = 1
    $mensagem = "ERRO NO BACKUP: $($_.Exception.Message)"
    if ($pastaProtegida) {
        Escrever $mensagem
        Set-Content -LiteralPath $falhaAnterior -Value ("{0} - {1}" -f (Get-Date), $mensagem)
    } else {
        Write-Output $mensagem
    }
}
finally {
    foreach ($arquivo in @($sql, $zipTemporario)) {
        if ($arquivo -and (Test-Path -LiteralPath $arquivo)) {
            Remove-Item -LiteralPath $arquivo -ErrorAction SilentlyContinue
        }
    }
}
exit $codigoSaida
