# =====================================================================================
#  Desinstalacao do SistemaPDV: desliga e remove o servico do MySQL e a tarefa de backup.
#  NAO apaga dados: o banco, a configuracao, os logs e os backups ficam em C:\ProgramData\SistemaPDV
#  (o desinstalador pergunta se e para apagar o banco; o padrao e manter).
#  Texto sem acentos de proposito.
# =====================================================================================

param(
    [string]$Servico = "SistemaPDV-MySQL",
    [string]$Tarefa  = "SistemaPDV - Backup"
)

$log = Join-Path $env:ProgramData "SistemaPDV\instalacao.log"
function Escrever([string]$texto) {
    try { Add-Content -Path $log -Value ("{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $texto) -Encoding UTF8 } catch { }
}

Escrever "==== Desinstalacao ===="

if (Get-ScheduledTask -TaskName $Tarefa -ErrorAction SilentlyContinue) {
    Unregister-ScheduledTask -TaskName $Tarefa -Confirm:$false
    Escrever "Tarefa '$Tarefa' removida"
}

$svc = Get-Service -Name $Servico -ErrorAction SilentlyContinue
if ($svc) {
    if ($svc.Status -ne "Stopped") {
        Stop-Service -Name $Servico -Force -ErrorAction SilentlyContinue
        # o MySQL termina de gravar antes de parar; espera ate 60 s para os arquivos ficarem livres
        $svc.WaitForStatus("Stopped", (New-TimeSpan -Seconds 60))
    }
    & sc.exe delete $Servico | Out-Null
    Escrever "Servico $Servico removido"
}
exit 0
