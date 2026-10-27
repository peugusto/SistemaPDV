# =====================================================================================
#  Configuracao do banco do SistemaPDV (o instalador roda isto como administrador)
#
#  Faz so o que ainda falta. Por isso pode rodar de novo sem estragar nada
#  (atualizacao, reinstalacao, ou depois de corrigir um erro):
#   1. Pastas em C:\ProgramData\SistemaPDV (logs liberados para o usuario da loja escrever).
#   2. my.ini do MySQL (porta 3306; se ela estiver ocupada, 3307, 3308...).
#   3. Pasta de dados do MySQL (so na primeira vez), fechada so para administradores.
#   4. Servico do Windows "SistemaPDV-MySQL": liga sozinho com o PC e reinicia se cair.
#   5. Senha do root (primeira vez) e os usuarios pdv_app e pdv_migracao, com senhas
#      sorteadas, gravadas em C:\ProgramData\SistemaPDV\db.properties.
#   6. (-Backup) usuario pdv_backup, backup.cnf, nuvem.txt e a tarefa agendada (12:00 e 18:30).
#  As tabelas NAO sao criadas aqui: o SistemaPDV cria/atualiza ao abrir (Flyway).
#
#  O root nunca fica gravado: so passa por um arquivo temporario, apagado no fim.
#  Texto sem acentos de proposito: o PowerShell do Windows estraga acentos em arquivo sem BOM.
# =====================================================================================

param(
    # Pasta instalada: tem mysql\bin e instalacao\backup-casarural.ps1
    [Parameter(Mandatory = $true)] [string]$Programa,
    [string]$Dados = (Join-Path $env:ProgramData "SistemaPDV"),
    # Arquivo com a senha do root escrita pelo instalador. Apagado assim que e lido.
    [string]$ArquivoSenhaRoot = "",
    # Onde escrever a mensagem de erro para o instalador mostrar
    [string]$ArquivoErro = "",
    # 0 = 3306, ou a proxima livre (so na primeira vez; depois vale a do my.ini)
    [int]$Porta = 0,
    [switch]$Backup,
    [string]$Servico = "SistemaPDV-MySQL",
    # So no PC de desenvolvimento: MySQL como processo comum (sem servico, sem tarefa, sem mexer em permissoes)
    [switch]$Teste
)

$ErrorActionPreference = "Stop"

$bin         = Join-Path $Programa "mysql\bin"
$pastaMysql  = Join-Path $Dados "mysql"
$pastaDados  = Join-Path $pastaMysql "data"
$ini         = Join-Path $pastaMysql "my.ini"
$config      = Join-Path $Dados "db.properties"
$pastaBackup = Join-Path $Dados "backup"
$cnfBackup   = Join-Path $pastaBackup "backup.cnf"
$log         = Join-Path $Dados "instalacao.log"
$tarefa      = "SistemaPDV - Backup"

# Grupos pelo SID: o nome muda com o idioma do Windows ("Usuarios", "Administradores")
$USUARIOS = "*S-1-5-32-545"
$ADMINS   = "*S-1-5-32-544"
$SISTEMA  = "*S-1-5-18"

# Senha do root: so estes caracteres (nao precisam de escape no SQL nem no .cnf)
$CARACTERES_ROOT = '^[A-Za-z0-9!@#$%&*()_=+.,;:?-]{8,64}$'

$temporarios = New-Object System.Collections.ArrayList

function Escrever([string]$texto) {
    $linha = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $texto
    Add-Content -Path $log -Value $linha -Encoding UTF8
    Write-Output $linha
}

function GravarTexto([string]$caminho, [string]$texto) {
    [IO.File]::WriteAllText($caminho, $texto, (New-Object Text.UTF8Encoding $false))
}

# Senha aleatoria so com letras e numeros (sem caracteres que confundem: 0/O, 1/l/I)
function NovaSenha([int]$tamanho = 24) {
    $letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    $limite = 256 - (256 % $letras.Length)       # descarta bytes que deixariam letras mais provaveis
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $byte = New-Object byte[] 1
    $senha = ""
    while ($senha.Length -lt $tamanho) {
        $rng.GetBytes($byte)
        if ($byte[0] -lt $limite) { $senha += $letras[$byte[0] % $letras.Length] }
    }
    return $senha
}

# Roda um programa e devolve codigo, saida e erros (sem o PowerShell transformar avisos em excecao).
# $entrada vai pelo teclado do programa: assim senhas nao aparecem na linha de comando.
function Executar([string]$exe, [string[]]$argumentos, [string]$entrada = $null) {
    $psi = New-Object Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.Arguments = ($argumentos | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join " "
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $p = [Diagnostics.Process]::Start($psi)
    $erros = $p.StandardError.ReadToEndAsync()
    if ($entrada) { $p.StandardInput.Write($entrada) }
    $p.StandardInput.Close()
    $saida = $p.StandardOutput.ReadToEnd()
    $p.WaitForExit()
    return [pscustomobject]@{ Codigo = $p.ExitCode; Saida = $saida; Erro = $erros.Result }
}

function PortaOcupada([int]$p) {
    if (Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue) { return $true }
    try {
        $l = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $p)
        $l.Start(); $l.Stop()
        return $false
    } catch {
        return $true
    }
}

# Arquivo temporario de login do root (apagado no fim, mesmo com erro)
function CnfRoot([string]$senha, [int]$p) {
    $arq = Join-Path $env:TEMP ("pdv-root-{0}.cnf" -f [guid]::NewGuid().ToString("N"))
    GravarTexto $arq ("[client]`r`nuser=root`r`npassword=`"{0}`"`r`nhost=127.0.0.1`r`nport={1}`r`n" -f $senha, $p)
    [void]$temporarios.Add($arq)
    return $arq
}

function Sql([string]$cnf, [string]$comandos) {
    $r = Executar (Join-Path $bin "mysql.exe") @("--defaults-file=$cnf", "--batch", "--skip-column-names", "--connect-timeout=10") $comandos
    if ($r.Codigo -ne 0) { throw ("MySQL: " + ($r.Erro -replace "mysql: \[Warning\][^\r\n]*", "").Trim()) }
    return $r.Saida
}

function Restringir([string]$caminho, [switch]$Pasta) {
    # So administradores e o sistema: nem o usuario da loja le (dados dos clientes, senhas)
    if ($Teste) { return }
    $heranca = if ($Pasta) { "(OI)(CI)" } else { "" }
    & icacls $caminho /inheritance:r /grant:r "${ADMINS}:${heranca}F" "${SISTEMA}:${heranca}F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel ajustar as permissoes de $caminho" }
}

function MysqlResponde([int]$p) {
    # ping responde "alive" mesmo sem usuario: so quer saber se o servidor esta de pe
    $r = Executar (Join-Path $bin "mysqladmin.exe") @("--no-defaults", "--host=127.0.0.1", "--port=$p", "--connect-timeout=2", "ping")
    return ($r.Codigo -eq 0)
}

function EsperarMysql([int]$p, [int]$segundos = 120) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        if (MysqlResponde $p) { return }
        Start-Sleep -Seconds 2
    }
    throw "O MySQL nao respondeu em $segundos segundos. Veja $pastaMysql\mysql-erros.log"
}

try {
    foreach ($pasta in @($Dados, (Join-Path $Dados "logs"), $pastaMysql, (Join-Path $pastaMysql "arquivos"))) {
        if (-not (Test-Path $pasta)) { New-Item -ItemType Directory -Force -Path $pasta | Out-Null }
    }
    Escrever "==== Configuracao do banco (programa em $Programa) ===="

    # Usuario da loja (sem ser administrador) precisa gravar os logs do sistema
    if (-not $Teste) {
        & icacls (Join-Path $Dados "logs") /grant "${USUARIOS}:(OI)(CI)M" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel liberar a pasta de logs" }
    }

    if (-not (Test-Path (Join-Path $bin "mysqld.exe"))) { throw "MySQL nao encontrado em $bin" }
    $versao = Executar (Join-Path $bin "mysqld.exe") @("--version")
    if ($versao.Codigo -ne 0) {
        throw "O MySQL nao conseguiu rodar neste computador (codigo $($versao.Codigo)). Falta o Microsoft Visual C++ 2015-2022 (x64)?"
    }
    Escrever ("MySQL: " + $versao.Saida.Trim())

    # ---- senha do root vinda do instalador (le e apaga na hora) ----
    $senhaRoot = ""
    if ($ArquivoSenhaRoot -ne "" -and (Test-Path $ArquivoSenhaRoot)) {
        $senhaRoot = ([IO.File]::ReadAllText($ArquivoSenhaRoot)).Trim()
        Remove-Item $ArquivoSenhaRoot -Force
    }
    if ($senhaRoot -ne "" -and $senhaRoot -notmatch $CARACTERES_ROOT) {
        throw "A senha do root precisa ter de 8 a 64 caracteres: letras, numeros e !@#$%&*()-_=+.,;:?"
    }

    # ---- 2. my.ini ----
    if (Test-Path $ini) {
        $m = Select-String -Path $ini -Pattern '^\s*port\s*=\s*(\d+)' | Select-Object -First 1
        if (-not $m) { throw "Porta nao encontrada em $ini" }
        $porta = [int]$m.Matches[0].Groups[1].Value
        Escrever "my.ini ja existia (porta $porta)"
    } else {
        $porta = $Porta
        if ($porta -eq 0) {
            $porta = 3306
            while (PortaOcupada $porta) {
                Escrever "Porta $porta ocupada (outro MySQL neste PC?); tentando a proxima"
                $porta++
                if ($porta -gt 3320) { throw "Nenhuma porta livre entre 3306 e 3320" }
            }
        }
        $barra = { param($c) $c -replace '\\', '/' }
        GravarTexto $ini (@"
# MySQL do SistemaPDV. Gerado pelo instalador em $(Get-Date -Format "dd/MM/yyyy HH:mm").
# Depois de mudar algo aqui: reinicie o servico "$Servico" (services.msc).
[mysqld]
basedir="$(& $barra (Join-Path $Programa 'mysql'))"
datadir="$(& $barra $pastaDados)"
port=$porta
# So este computador acessa. Para um segundo caixa na rede, veja o LEIA-ME do instalador.
bind-address=127.0.0.1
mysqlx=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
# Loja pequena: pouca memoria basta
innodb_buffer_pool_size=256M
max_connections=50
# Registro de alteracoes (binlog) guardado por 7 dias
binlog_expire_logs_seconds=604800
log-error="$(& $barra (Join-Path $pastaMysql 'mysql-erros.log'))"
secure-file-priv="$(& $barra (Join-Path $pastaMysql 'arquivos'))"
"@)
        Escrever "my.ini criado (porta $porta)"
    }

    # ---- 3. pasta de dados ----
    $novo = $false
    if (-not (Test-Path (Join-Path $pastaDados "mysql.ibd"))) {
        if ((Test-Path $pastaDados) -and (Get-ChildItem $pastaDados -Force | Select-Object -First 1)) {
            throw "A pasta $pastaDados tem arquivos, mas nao e um banco completo. Nada foi apagado; confira a pasta a mao."
        }
        Escrever "Criando a pasta de dados do MySQL (primeira instalacao)..."
        $r = Executar (Join-Path $bin "mysqld.exe") @("--defaults-file=$ini", "--initialize-insecure", "--console")
        Add-Content -Path $log -Value $r.Erro -Encoding UTF8
        if ($r.Codigo -ne 0) { throw "O MySQL nao conseguiu criar a pasta de dados (codigo $($r.Codigo)). Detalhes em $log" }
        $novo = $true
        # banco novo = usuarios novos; um db.properties antigo nao serve mais
        if (Test-Path $config) {
            $antigo = "$config.antigo-" + (Get-Date -Format "yyyyMMdd-HHmm")
            Move-Item $config $antigo
            Escrever "db.properties de uma instalacao anterior guardado como $antigo"
        }
    }
    Restringir $pastaDados -Pasta

    # ---- 4. servidor ligado ----
    if ($Teste) {
        if (-not (MysqlResponde $porta)) {
            Start-Process -FilePath (Join-Path $bin "mysqld.exe") -ArgumentList "`"--defaults-file=$ini`"" -WindowStyle Hidden | Out-Null
            Escrever "MySQL iniciado como processo (modo teste)"
        }
    } else {
        if (-not (Get-Service -Name $Servico -ErrorAction SilentlyContinue)) {
            $r = Executar (Join-Path $bin "mysqld.exe") @("--install", $Servico, "--defaults-file=$ini")
            if ($r.Codigo -ne 0) { throw "Nao foi possivel criar o servico $Servico : $($r.Saida) $($r.Erro)" }
            & sc.exe description $Servico "Banco de dados do SistemaPDV (Casa Rural)" | Out-Null
            # Se o MySQL cair, o Windows liga de novo (5 s, 5 s, depois 30 s)
            & sc.exe failure $Servico reset= 86400 actions= restart/5000/restart/5000/restart/30000 | Out-Null
            Escrever "Servico $Servico criado (inicia com o Windows)"
        }
        Set-Service -Name $Servico -StartupType Automatic
        if ((Get-Service -Name $Servico).Status -ne "Running") { Start-Service -Name $Servico }
    }
    EsperarMysql $porta
    Escrever "MySQL respondendo na porta $porta"

    # ---- 5. root e usuarios do sistema ----
    $precisaRoot = $novo -or -not (Test-Path $config) -or ($Backup -and -not (Test-Path $cnfBackup))
    $cnf = $null
    if ($precisaRoot) {
        if ($senhaRoot -eq "") { throw "Faltou a senha do root (o instalador pede quando precisa criar usuarios)." }
        if ($novo) {
            Sql (CnfRoot "" $porta) "ALTER USER 'root'@'localhost' IDENTIFIED BY '$senhaRoot';" | Out-Null
            Escrever "Senha do root definida"
        }
        $cnf = CnfRoot $senhaRoot $porta
        try {
            Sql $cnf "SELECT 1;" | Out-Null
        } catch {
            throw "A senha do root nao confere com a do banco que ja existe neste computador. Execute o instalador de novo com a senha certa."
        }
    }

    if (-not (Test-Path $config)) {
        $senhaApp = NovaSenha
        $senhaMigracao = NovaSenha
        Sql $cnf @"
CREATE DATABASE IF NOT EXISTS casarural CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'pdv_app'@'localhost' IDENTIFIED BY '$senhaApp';
ALTER USER 'pdv_app'@'localhost' IDENTIFIED BY '$senhaApp';
GRANT SELECT, INSERT, UPDATE, DELETE ON casarural.* TO 'pdv_app'@'localhost';
CREATE USER IF NOT EXISTS 'pdv_migracao'@'localhost' IDENTIFIED BY '$senhaMigracao';
ALTER USER 'pdv_migracao'@'localhost' IDENTIFIED BY '$senhaMigracao';
GRANT ALL PRIVILEGES ON casarural.* TO 'pdv_migracao'@'localhost';
"@ | Out-Null
        GravarTexto $config (@"
# Configuracao do banco do SistemaPDV. Gerado pelo instalador em $(Get-Date -Format "dd/MM/yyyy HH:mm").
# As senhas foram sorteadas pelo instalador. O root nao fica aqui.
#   pdv_app      -> usado o tempo todo; so le e altera dados
#   pdv_migracao -> usado ao abrir, para criar/atualizar as tabelas
# Para trocar uma senha: ALTER USER no Workbench (como root) e troque aqui tambem.
dburl=jdbc:mysql://localhost:$porta/casarural?allowPublicKeyRetrieval=true&useSSL=false
user=pdv_app
password=$senhaApp

migracao.user=pdv_migracao
migracao.password=$senhaMigracao
"@)
        Escrever "Usuarios pdv_app e pdv_migracao criados; configuracao em $config"
    } else {
        Escrever "db.properties ja existia; usuarios mantidos"
    }

    # ---- 6. backup ----
    if ($Backup) {
        if (-not (Test-Path $pastaBackup)) { New-Item -ItemType Directory -Force -Path $pastaBackup | Out-Null }
        Copy-Item (Join-Path $Programa "instalacao\backup-casarural.ps1") $pastaBackup -Force

        if (-not (Test-Path $cnfBackup)) {
            $senhaBackup = NovaSenha
            Sql $cnf @"
CREATE USER IF NOT EXISTS 'pdv_backup'@'localhost' IDENTIFIED BY '$senhaBackup';
ALTER USER 'pdv_backup'@'localhost' IDENTIFIED BY '$senhaBackup';
GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES ON casarural.* TO 'pdv_backup'@'localhost';
"@ | Out-Null
            GravarTexto $cnfBackup "# Usuario do backup (so le o banco). Gerado pelo instalador.`r`n[mysqldump]`r`nuser=pdv_backup`r`npassword=$senhaBackup`r`nhost=localhost`r`nport=$porta`r`n"
            Restringir $cnfBackup
            Escrever "Usuario pdv_backup criado; senha em $cnfBackup"
        }

        $nuvem = Join-Path $pastaBackup "nuvem.txt"
        if (-not (Test-Path $nuvem)) {
            GravarTexto $nuvem (@"
# Pasta do OneDrive / Google Drive para onde vai uma copia de cada backup.
# Escreva o caminho na primeira linha sem # (exemplo abaixo) e salve. Vale a partir do proximo backup.
# Deixe sem nenhuma linha (so com #) para guardar so neste computador.
#
# C:\Users\Loja\OneDrive\Backup Casa Rural
"@)
        }

        if (-not $Teste) {
            $nomeSistema = (New-Object Security.Principal.SecurityIdentifier "S-1-5-18").Translate([Security.Principal.NTAccount]).Value
            $script = Join-Path $pastaBackup "backup-casarural.ps1"
            $acao = New-ScheduledTaskAction -Execute "powershell.exe" `
                -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "{0}" -MysqlBin "{1}"' -f $script, $bin)
            $gatilhos = @((New-ScheduledTaskTrigger -Daily -At ([datetime]::Today.AddHours(12))),
                          (New-ScheduledTaskTrigger -Daily -At ([datetime]::Today.AddHours(18.5))))
            # Se o PC estava desligado no horario, faz quando ligar
            $ajustes = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                -ExecutionTimeLimit (New-TimeSpan -Hours 1)
            $quem = New-ScheduledTaskPrincipal -UserId $nomeSistema -LogonType ServiceAccount -RunLevel Highest
            Register-ScheduledTask -TaskName $tarefa -Action $acao -Trigger $gatilhos -Settings $ajustes -Principal $quem `
                -Description "Backup do banco do SistemaPDV. Copias em $pastaBackup\copias" -Force | Out-Null
            Escrever "Tarefa agendada '$tarefa' (12:00 e 18:30)"
        }
    }

    Escrever "Pronto. Banco na porta $porta, configuracao em $config"
    exit 0
}
catch {
    $mensagem = $_.Exception.Message
    try { Escrever "ERRO: $mensagem" } catch { }
    if ($ArquivoErro -ne "") { GravarTexto $ArquivoErro $mensagem }
    exit 1
}
finally {
    foreach ($t in $temporarios) { Remove-Item $t -Force -ErrorAction SilentlyContinue }
    if ($ArquivoSenhaRoot -ne "") { Remove-Item $ArquivoSenhaRoot -Force -ErrorAction SilentlyContinue }
}
