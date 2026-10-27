# =====================================================================================
#  Gera o instalador do SistemaPDV:  instalador\saida\Instalar-SistemaPDV-<versao>.exe
#
#  Como usar (PowerShell, na pasta do projeto):
#    powershell -ExecutionPolicy Bypass -File instalador\gerar-instalador.ps1
#
#  Passos:
#   1. Compila e roda os testes (mvnw clean package). Teste falhando = para aqui.
#   2. Junta o sistema e as bibliotecas (target\instalador\mods).
#   3. Monta um Java 21 enxuto, so com o que o sistema usa (jlink).
#   4. Cria o SistemaPDV.exe com esse Java dentro (jpackage).
#   5. Junta o MySQL 8.4 sem os arquivos de depuracao, e os scripts de instalacao.
#   6. Compila o instalador (Inno Setup).
#
#  Precisa (uma vez so, neste PC):
#   - JDK 21 (o mesmo da IntelliJ). Procura em JAVA_HOME e em %USERPROFILE%\.jdks\ms-21*
#   - MySQL 8.4 LTS "Windows ZIP Archive" descompactado em instalador\terceiros\mysql-8.4.*-winx64
#     (dev.mysql.com/downloads/mysql; confira o MD5 mostrado no site)
#   - Inno Setup 7 (jrsoftware.org)
#  A versao vem do pom.xml (<version>): aumente antes de gerar um instalador novo para a loja.
#  Texto sem acentos de proposito: o PowerShell do Windows estraga acentos em arquivo sem BOM.
# =====================================================================================

param(
    [string]$Jdk   = "",    # pasta do JDK 21
    [string]$Mysql = "",    # pasta do MySQL 8.4 descompactado
    [string]$Iscc  = ""     # ISCC.exe do Inno Setup
)

$ErrorActionPreference = "Stop"
$raiz   = Split-Path $PSScriptRoot -Parent
$alvo   = Join-Path $raiz "target\instalador"
$mods   = Join-Path $alvo "mods"
$pacote = Join-Path $alvo "SistemaPDV"     # exatamente o que vai para C:\Program Files\SistemaPDV

function Passo([string]$texto) { Write-Host ""; Write-Host "==> $texto" -ForegroundColor Cyan }
function Conferir([string]$oque) { if ($LASTEXITCODE -ne 0) { throw "$oque falhou (codigo $LASTEXITCODE)" } }

# ---------------- ferramentas ----------------
if ($Jdk -eq "") {
    $candidatos = @($env:JAVA_HOME)
    $candidatos += Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "ms-21*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName }
    $candidatos += Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName }
    $Jdk = $candidatos | Where-Object {
        $_ -and (Test-Path "$_\bin\jpackage.exe") -and (Test-Path "$_\jmods") -and
        ((Get-Content "$_\release" -ErrorAction SilentlyContinue) -match 'JAVA_VERSION="21')
    } | Select-Object -First 1
}
if (-not $Jdk) { throw "JDK 21 nao encontrado. Informe com -Jdk C:\caminho\do\jdk-21" }

if ($Mysql -eq "") {
    $Mysql = Get-ChildItem (Join-Path $PSScriptRoot "terceiros") -Directory -Filter "mysql-8.4*-winx64" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1 | ForEach-Object { $_.FullName }
}
if (-not $Mysql -or -not (Test-Path "$Mysql\bin\mysqld.exe")) {
    throw "MySQL 8.4 nao encontrado. Descompacte o ZIP em instalador\terceiros (ou informe -Mysql)."
}

if ($Iscc -eq "") {
    $Iscc = @("$env:LOCALAPPDATA\Programs\Inno Setup 7\ISCC.exe",
              "$env:ProgramFiles\Inno Setup 7\ISCC.exe",
              "${env:ProgramFiles(x86)}\Inno Setup 7\ISCC.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $Iscc) { throw "Inno Setup 7 nao encontrado (ISCC.exe). Informe com -Iscc" }

[xml]$pom = Get-Content (Join-Path $raiz "pom.xml") -Raw -Encoding UTF8
$versao = $pom.project.version
if ($versao -notmatch '^\d+\.\d+\.\d+$') {
    throw "A versao do pom.xml ($versao) precisa ser so numeros, por exemplo 1.0.1 (sem -SNAPSHOT)."
}

Write-Host "Versao: $versao"
Write-Host "JDK:    $Jdk"
Write-Host "MySQL:  $Mysql"
Write-Host "Inno:   $Iscc"

# ---------------- 1. compila e testa ----------------
Passo "Compilando e rodando os testes"
$env:JAVA_HOME = $Jdk
Push-Location $raiz
try {
    & (Join-Path $raiz "mvnw.cmd") -B -q clean package
    Conferir "Compilacao/testes"

    # ---------------- 2. bibliotecas ----------------
    Passo "Juntando as bibliotecas"
    & (Join-Path $raiz "mvnw.cmd") -B -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies `
        -DincludeScope=runtime "-DoutputDirectory=$mods"
    Conferir "Copia das bibliotecas"
} finally {
    Pop-Location
}
Copy-Item (Join-Path $raiz "target\SistemaPDV-$versao.jar") (Join-Path $mods "sistemapdv-$versao.jar")
# O JavaFX vem em dois jars: um vazio (sem sufixo) e o de verdade (-win). Fica so o de verdade.
Get-ChildItem $mods -Filter "javafx-*.jar" | Where-Object { $_.Name -notlike "*-win.jar" } | Remove-Item

# ---------------- 3. Java enxuto ----------------
Passo "Montando o Java enxuto (jlink)"
# Modulos do Java usados pelo sistema e bibliotecas (jdeps --print-module-deps), mais:
#   jdk.localedata (formatos do Brasil), jdk.charsets, jdk.crypto.ec (TLS), jdk.zipfs
$modulosJava = "java.base,java.desktop,java.management,java.naming,java.scripting,java.security.sasl," +
               "java.sql,jdk.jfr,jdk.unsupported,jdk.localedata,jdk.charsets,jdk.crypto.ec,jdk.zipfs"
& "$Jdk\bin\jlink.exe" --add-modules $modulosJava --include-locales=pt,en --strip-debug --no-header-files `
    --no-man-pages --compress=zip-6 --output (Join-Path $alvo "runtime")
Conferir "jlink"

# ---------------- 4. SistemaPDV.exe ----------------
Passo "Criando o SistemaPDV.exe (jpackage)"
# ALL-MODULE-PATH: carrega todas as bibliotecas da pasta mods, inclusive as que o sistema nao
# declara no module-info (o Flyway precisa do Jackson, por exemplo)
& "$Jdk\bin\jpackage.exe" --type app-image --name SistemaPDV --app-version $versao `
    --vendor "Casa Rural" --description "SistemaPDV - Casa Rural" `
    --icon (Join-Path $PSScriptRoot "icone.ico") `
    --runtime-image (Join-Path $alvo "runtime") `
    --module-path $mods --module com.casarural.sistemapdv/com.casarural.sistemapdv.Main `
    --java-options "--add-modules=ALL-MODULE-PATH" `
    --dest $alvo
Conferir "jpackage"

# ---------------- 5. MySQL e scripts ----------------
Passo "Juntando o MySQL (sem arquivos de depuracao)"
# Fica de fora: simbolos de depuracao, arquivos para programar em C, o dicionario japones (mecab,
# 130 MB, so serve para busca em texto japones) e ferramentas que o sistema nunca usa.
& robocopy $Mysql (Join-Path $pacote "mysql") /E /XD include docs debug mecab `
    /XF *.pdb *.lib *-debug.dll mysql_configurator.exe myisam*.exe mysqlslap.exe `
        mysql_migrate_keyring.exe mysql_secure_installation.exe /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "Copia do MySQL falhou (robocopy $LASTEXITCODE)" }
# O MySQL precisa do Visual C++ 2015-2022, que nem todo Windows tem. Vai junto (os mesmos
# arquivos que o JDK distribui; o MySQL foi compilado com uma versao mais antiga, entao servem).
foreach ($dll in @("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll")) {
    Copy-Item (Join-Path "$Jdk\bin" $dll) (Join-Path $pacote "mysql\bin") -Force
}

$instalacao = Join-Path $pacote "instalacao"
New-Item -ItemType Directory -Force -Path $instalacao | Out-Null
Copy-Item (Join-Path $PSScriptRoot "scripts\configurar-banco.ps1") $instalacao
Copy-Item (Join-Path $PSScriptRoot "scripts\remover.ps1") $instalacao
Copy-Item (Join-Path $PSScriptRoot "LEIA-ME.txt") $instalacao
Copy-Item (Join-Path $raiz "config\backup\backup-casarural.ps1") $instalacao
Copy-Item (Join-Path $raiz "config\backup\LEIA-ME.txt") (Join-Path $instalacao "backup-LEIA-ME.txt")

$tamanho = (Get-ChildItem $pacote -Recurse -File | Measure-Object Length -Sum).Sum / 1MB
Write-Host ("Pacote: {0:N0} MB em {1}" -f $tamanho, $pacote)

# ---------------- 6. instalador ----------------
Passo "Compilando o instalador (Inno Setup) - leva alguns minutos"
$saida = Join-Path $PSScriptRoot "saida"
& $Iscc /Q "/DVersao=$versao" "/DPacote=$pacote" "/O$saida" (Join-Path $PSScriptRoot "SistemaPDV.iss")
Conferir "Inno Setup"

$exe = Get-Item (Join-Path $saida "Instalar-SistemaPDV-$versao.exe")
$hash = (Get-FileHash $exe.FullName -Algorithm SHA256).Hash
Passo "Pronto"
Write-Host ("{0}  ({1:N0} MB)" -f $exe.FullName, ($exe.Length / 1MB))
Write-Host "SHA-256: $hash"
Write-Host "Leve para a loja num pendrive e confira o SHA-256 la (Get-FileHash) antes de instalar."
