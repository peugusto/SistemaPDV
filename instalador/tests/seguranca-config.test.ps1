param([Parameter(Mandatory=$true)][string]$PastaTeste)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '..\scripts\seguranca-config.ps1')
function Conferir($condicao,[string]$mensagem) { if (-not $condicao) { throw $mensagem } }
$secoes=[Security.AccessControl.AccessControlSections]::All
$adm='S-1-5-32-544';$sistema='S-1-5-18';$usuarios='S-1-5-32-545'
foreach ($pasta in @($false,$true)) {
    $acl=Nova-AclPdv -Pasta:$pasta
    Conferir $acl.AreAccessRulesProtected 'Segredo herdou permissoes'
    Conferir ($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -eq $adm) 'Proprietario administrativo incorreto'
    $regras=$acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])
    Conferir ($regras.Count -eq 2) 'Segredo possui ACE extra'
    foreach ($regra in $regras) {
        Conferir ($regra.IdentityReference.Value -in @($adm,$sistema)) 'Segredo acessivel a outra identidade'
        Conferir ($regra.FileSystemRights -eq [Security.AccessControl.FileSystemRights]::FullControl) 'Permissao administrativa incompleta'
    }
}
$acl=Nova-AclPdv -LeituraUsuarios
$regra=@($acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]) | Where-Object {$_.IdentityReference.Value -eq $usuarios})
Conferir ($regra.Count -eq 1) 'Credencial de execucao nao libera leitura'
Conferir (($regra[0].FileSystemRights -band [Security.AccessControl.FileSystemRights]::Write) -eq 0) 'Users pode escrever na configuracao'
Conferir (($regra[0].FileSystemRights -band [Security.AccessControl.FileSystemRights]::ChangePermissions) -eq 0) 'Users pode mudar DACL'

# Mutacoes somente em uma pasta nova de teste. O proprietario de teste permite executar sem elevacao.
$dono=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$raiz=Join-Path ([IO.Path]::GetFullPath($PastaTeste)) ('acl-'+[guid]::NewGuid().ToString('N'))
Proteger-PastaPdv $raiz -Proprietario $dono
$arquivo=Join-Path $raiz 'segredo-ficticio.properties'
Preparar-ArquivoPdv $arquivo -Proprietario $dono
[IO.File]::WriteAllText($arquivo,'SEGREDO_SINTETICO')
$info=New-Object IO.FileInfo $arquivo
$antiga=$info.GetAccessControl([Security.AccessControl.AccessControlSections]::Access)
$antiga.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule(
    (New-Object Security.Principal.SecurityIdentifier 'S-1-1-0'),'FullControl','Allow')))
$info.SetAccessControl($antiga)
Preparar-ArquivoPdv $arquivo -Proprietario $dono
$atual=Get-Acl -LiteralPath $arquivo
Conferir $atual.AreAccessRulesProtected 'Arquivo real herdou permissoes'
Conferir (@($atual.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]) | Where-Object {$_.IdentityReference.Value -eq 'S-1-1-0'}).Count -eq 0) 'ACE explicita Everyone sobreviveu'
Conferir ([IO.File]::ReadAllText($arquivo) -eq 'SEGREDO_SINTETICO') 'Ajustar ACL alterou conteudo'
$destino=Join-Path $raiz 'destino';New-Item -ItemType Directory -Path $destino | Out-Null
$atalho=Join-Path $raiz 'redirecionado';New-Item -ItemType Junction -Path $atalho -Target $destino | Out-Null
$recusou=$false
try { Preparar-ArquivoPdv (Join-Path $atalho 'nao-criar.properties') -Proprietario $dono } catch { $recusou=$true }
Conferir $recusou 'Caminho redirecionado foi aceito'
Conferir (-not (Test-Path -LiteralPath (Join-Path $destino 'nao-criar.properties'))) 'Criou arquivo atraves do redirecionamento'
Write-Output 'PASS: DACL administrativa exata, runtime somente leitura, remocao de ACE antiga e recusa de junction; somente dados sinteticos.'
