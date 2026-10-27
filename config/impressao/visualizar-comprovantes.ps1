# Modo temporário: só este processo e seus filhos recebem a variável.
$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$previousPrintMode = $env:PDV_IMPRESSAO_MODO
Push-Location -LiteralPath $projectDirectory
try {
    $env:PDV_IMPRESSAO_MODO = 'previa'
    Write-Host 'Prévia ativada: os comprovantes aparecerão neste terminal. Nada será enviado à impressora.'
    & (Join-Path $projectDirectory 'mvnw.cmd') 'javafx:run'
    if ($LASTEXITCODE -ne 0) { throw 'O sistema não pôde ser iniciado. Confira a saída do Maven acima.' }
} finally {
    if ($null -eq $previousPrintMode) {
        Remove-Item Env:PDV_IMPRESSAO_MODO -ErrorAction SilentlyContinue
    } else {
        $env:PDV_IMPRESSAO_MODO = $previousPrintMode
    }
    Pop-Location
}
