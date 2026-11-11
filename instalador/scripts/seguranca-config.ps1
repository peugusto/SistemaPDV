# Funcoes sem efeitos ao importar. Windows PowerShell 5.1 / .NET Framework.
function Confirmar-CaminhoPdv([string]$Caminho) {
    $atual = [IO.Path]::GetFullPath($Caminho)
    while ($atual) {
        if (Test-Path -LiteralPath $atual) {
            $item = Get-Item -LiteralPath $atual -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Caminho redirecionado nao permitido na configuracao do banco.' }
        }
        $pai = Split-Path -Path $atual -Parent
        if ($pai -eq $atual) { break }
        $atual = $pai
    }
}

function Nova-AclPdv([switch]$Pasta, [switch]$LeituraUsuarios,
                     [string]$Proprietario = 'S-1-5-32-544') {
    $acl = if ($Pasta) { New-Object Security.AccessControl.DirectorySecurity } else { New-Object Security.AccessControl.FileSecurity }
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner((New-Object Security.Principal.SecurityIdentifier $Proprietario))
    $heranca = if ($Pasta) { [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit' } else { [Security.AccessControl.InheritanceFlags]::None }
    foreach ($sid in (@($Proprietario,'S-1-5-32-544','S-1-5-18') | Select-Object -Unique)) {
        $regra = New-Object Security.AccessControl.FileSystemAccessRule(
            (New-Object Security.Principal.SecurityIdentifier $sid), 'FullControl', $heranca, 'None', 'Allow')
        [void]$acl.AddAccessRule($regra)
    }
    if ($LeituraUsuarios) {
        $direito = if ($Pasta) { 'ReadAndExecute' } else { 'Read' }
        [void]$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule(
            (New-Object Security.Principal.SecurityIdentifier 'S-1-5-32-545'), $direito, $heranca, 'None', 'Allow')))
    }
    return $acl
}

function Aplicar-AclPdv([IO.FileSystemInfo]$Item, [Security.AccessControl.FileSystemSecurity]$Acl) {
    $atual = $Item.GetAccessControl()
    $secao = [Security.AccessControl.AccessControlSections]::Access
    $atual.SetSecurityDescriptorSddlForm($Acl.GetSecurityDescriptorSddlForm($secao), $secao)
    $tipoSid = [Security.Principal.SecurityIdentifier]
    $proprietario = $Acl.GetOwner($tipoSid)
    if ($atual.GetOwner($tipoSid).Value -ne $proprietario.Value) {
        $atual.SetOwner($proprietario)
    }
    $Item.SetAccessControl($atual)
}

function Proteger-PastaPdv([string]$Caminho, [switch]$LeituraUsuarios,
                          [string]$Proprietario = 'S-1-5-32-544') {
    Confirmar-CaminhoPdv $Caminho
    $acl = Nova-AclPdv -Pasta -LeituraUsuarios:$LeituraUsuarios -Proprietario $Proprietario
    $pasta = New-Object IO.DirectoryInfo ([IO.Path]::GetFullPath($Caminho))
    # Create(ACL) evita uma janela de criacao com permissoes herdadas.
    $pasta.Create($acl)
    Aplicar-AclPdv $pasta $acl
}

function Preparar-ArquivoPdv([string]$Caminho, [switch]$LeituraUsuarios,
                            [string]$Proprietario = 'S-1-5-32-544') {
    Confirmar-CaminhoPdv $Caminho
    $acl = Nova-AclPdv -LeituraUsuarios:$LeituraUsuarios -Proprietario $Proprietario
    $arquivo = New-Object IO.FileInfo ([IO.Path]::GetFullPath($Caminho))
    if ($arquivo.Exists) {
        Aplicar-AclPdv $arquivo $acl
    } else {
        $stream = New-Object IO.FileStream($arquivo.FullName, [IO.FileMode]::CreateNew,
            [Security.AccessControl.FileSystemRights]::Write, [IO.FileShare]::None, 4096,
            [IO.FileOptions]::None, $acl)
        $stream.Dispose()
    }
}

function Proteger-CopiasBackupPdv([string]$Caminho, [string]$Proprietario = 'S-1-5-32-544') {
    Proteger-PastaPdv $Caminho -Proprietario $Proprietario
    foreach ($arquivo in (Get-ChildItem -LiteralPath $Caminho -File -Force)) {
        if ($arquivo.Name -like 'casarural_*.sql' -or $arquivo.Name -like 'casarural_*.zip' -or
                $arquivo.Name -in @('backup.log', 'ULTIMO_BACKUP_FALHOU.txt')) {
            Preparar-ArquivoPdv $arquivo.FullName -Proprietario $Proprietario
        }
    }
}
