; =====================================================================================
;  Instalador do SistemaPDV (Casa Rural) - Inno Setup 7
;
;  Não compile este arquivo direto: rode gerar-instalador.ps1. Ele compila o sistema,
;  monta o pacote (programa + Java + MySQL) e passa a versão e a pasta do pacote para cá.
;
;  O que o instalador faz no PC da loja:
;   - copia o programa para C:\Program Files\SistemaPDV (com o Java e o MySQL embutidos);
;   - roda instalacao\configurar-banco.ps1: MySQL como serviço do Windows, usuários com
;     senhas sorteadas, db.properties e, se marcado, o backup automático;
;   - cria os atalhos.
;  Rodar de novo (versão nova) só troca o programa: o banco e a configuração ficam.
; =====================================================================================

#ifndef Versao
  #error Rode gerar-instalador.ps1 em vez de compilar este arquivo direto.
#endif
#ifndef Pacote
  #error Rode gerar-instalador.ps1 em vez de compilar este arquivo direto.
#endif

#define Nome "SistemaPDV - Casa Rural"
#define Servico "SistemaPDV-MySQL"
#define PowerShell "{sys}\WindowsPowerShell\v1.0\powershell.exe"

[Setup]
; Nunca mude o AppId: é por ele que o Windows sabe que a versão nova substitui a antiga
AppId={{C1C4C8CE-2D17-49CC-A52A-0C9A330148BE}
AppName={#Nome}
AppVersion={#Versao}
AppVerName={#Nome} {#Versao}
AppPublisher=Pedro - Casa Rural
VersionInfoVersion={#Versao}
VersionInfoDescription=Instalador do SistemaPDV
; Pasta fixa: o backup agendado e o my.ini apontam para ela
DefaultDirName={autopf}\SistemaPDV
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
WizardStyle=modern
SetupIconFile=icone.ico
UninstallDisplayIcon={app}\SistemaPDV.exe
UninstallDisplayName={#Nome}
OutputBaseFilename=Instalar-SistemaPDV-{#Versao}
Compression=lzma2/max
SolidCompression=yes
; Fecha o SistemaPDV se estiver aberto durante uma atualização
CloseApplications=yes
RestartApplications=no
ShowLanguageDialog=no
SetupLogging=yes

[Languages]
Name: "ptbr"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Tasks]
Name: "atalho"; Description: "Criar atalho na Área de Trabalho"; GroupDescription: "Atalhos:"
Name: "backup"; Description: "Backup automático do banco todo dia, às 12:00 e às 18:30"; GroupDescription: "Banco de dados:"

[InstallDelete]
; Numa atualização, bibliotecas de versões antigas não podem sobrar junto com as novas.
; Os dados não estão aqui (ficam em C:\ProgramData\SistemaPDV).
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\mysql"

[Files]
Source: "{#Pacote}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#Nome}"; Filename: "{app}\SistemaPDV.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\{#Nome}"; Filename: "{app}\SistemaPDV.exe"; WorkingDir: "{app}"; Tasks: atalho

[Run]
; runasoriginaluser: abre como o usuário normal, não como administrador
Filename: "{app}\SistemaPDV.exe"; Description: "Abrir o SistemaPDV agora"; WorkingDir: "{app}"; Flags: postinstall nowait skipifsilent runasoriginaluser; Check: BancoConfigurado

[UninstallRun]
; Desliga e remove o serviço do MySQL e a tarefa de backup (os dados ficam)
Filename: "{#PowerShell}"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\instalacao\remover.ps1"""; Flags: runhidden waituntilterminated; RunOnceId: "RemoverServico"

[Code]
var
  PaginaSenha: TInputQueryWizardPage;
  BancoOk: Boolean;

function PastaDados: String;
begin
  Result := ExpandConstant('{commonappdata}\SistemaPDV');
end;

function BancoJaExiste: Boolean;
begin
  Result := FileExists(PastaDados + '\mysql\data\mysql.ibd');
end;

{ A senha do root só é pedida quando é preciso criar usuários no MySQL:
  primeira instalação, configuração apagada, ou backup marcado pela primeira vez.
  Numa atualização comum a página nem aparece. }
function PrecisaSenhaRoot: Boolean;
begin
  Result := (not BancoJaExiste)
    or (not FileExists(PastaDados + '\db.properties'))
    or (WizardIsTaskSelected('backup') and not FileExists(PastaDados + '\backup\backup.cnf'));
end;

function BancoConfigurado: Boolean;
begin
  Result := BancoOk;
end;

procedure InitializeWizard;
begin
  PaginaSenha := CreateInputQueryPage(wpSelectTasks,
    'Senha do banco de dados', 'Senha do administrador (root) do MySQL', '');
  PaginaSenha.Add('Senha:', True);
  PaginaSenha.Add('Repita a senha:', True);
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = PaginaSenha.ID then
  begin
    if BancoJaExiste then
      PaginaSenha.SubCaptionLabel.Caption :=
        'Já existe um banco do SistemaPDV neste computador. Digite a senha do root ' +
        'que foi escolhida na primeira instalação.'
    else
      PaginaSenha.SubCaptionLabel.Caption :=
        'Escolha a senha do administrador do banco (usuário root). O sistema não usa ' +
        'essa senha no dia a dia: ela serve para manutenção no MySQL Workbench.' + #13#10#13#10 +
        'Guarde-a no seu gerenciador de senhas. Use de 8 a 64 caracteres: letras sem acento, ' +
        'números e  ! @ # $ % & * ( ) - _ = + . , ; : ?';
  end;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := (PaginaSenha <> nil) and (PageID = PaginaSenha.ID) and not PrecisaSenhaRoot;
end;

{ Os mesmos caracteres que o configurar-banco.ps1 aceita: nenhum precisa de escape no SQL nem no .cnf }
function SenhaValida(const S: String): Boolean;
var
  I: Integer;
  C: Char;
begin
  Result := (Length(S) >= 8) and (Length(S) <= 64);
  for I := 1 to Length(S) do
  begin
    C := S[I];
    if not (((C >= 'A') and (C <= 'Z')) or ((C >= 'a') and (C <= 'z')) or
            ((C >= '0') and (C <= '9')) or (Pos(C, '!@#$%&*()_=+.,;:?-') > 0)) then
      Result := False;
  end;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if CurPageID = PaginaSenha.ID then
  begin
    if not SenhaValida(PaginaSenha.Values[0]) then
    begin
      MsgBox('A senha precisa ter de 8 a 64 caracteres: letras sem acento, números e ' +
        '! @ # $ % & * ( ) - _ = + . , ; : ?', mbError, MB_OK);
      Result := False;
    end
    else if PaginaSenha.Values[0] <> PaginaSenha.Values[1] then
    begin
      MsgBox('As duas senhas não são iguais.', mbError, MB_OK);
      Result := False;
    end;
  end;
end;

{ Atualização: desliga o MySQL do sistema para poder trocar os arquivos dele.
  O configurar-banco.ps1 liga de novo no fim. }
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Codigo: Integer;
begin
  Result := '';
  Exec(ExpandConstant('{#PowerShell}'),
    '-NoProfile -Command "Stop-Service -Name ''{#Servico}'' -Force -ErrorAction SilentlyContinue"',
    '', SW_HIDE, ewWaitUntilTerminated, Codigo);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Parametros, ArqSenha, ArqErro, Erro: String;
  ErroLido: AnsiString;
  Codigo: Integer;
begin
  if CurStep <> ssPostInstall then
    Exit;

  ArqSenha := ExpandConstant('{tmp}\root.txt');
  ArqErro := ExpandConstant('{tmp}\erro.txt');
  Parametros := '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\instalacao\configurar-banco.ps1') + '"' +
    ' -Programa "' + ExpandConstant('{app}') + '" -ArquivoErro "' + ArqErro + '"';
  { A senha vai por um arquivo temporário (apagado pelo script assim que lê), nunca pela linha de comando }
  if PaginaSenha.Values[0] <> '' then
  begin
    SaveStringToFile(ArqSenha, PaginaSenha.Values[0], False);
    Parametros := Parametros + ' -ArquivoSenhaRoot "' + ArqSenha + '"';
  end;
  if WizardIsTaskSelected('backup') then
    Parametros := Parametros + ' -Backup';

  WizardForm.StatusLabel.Caption := 'Configurando o banco de dados (pode levar até 1 minuto)...';
  WizardForm.ProgressGauge.Style := npbstMarquee;
  try
    BancoOk := Exec(ExpandConstant('{#PowerShell}'), Parametros, '', SW_HIDE, ewWaitUntilTerminated, Codigo)
      and (Codigo = 0);
  finally
    WizardForm.ProgressGauge.Style := npbstNormal;
    DeleteFile(ArqSenha);
  end;

  if not BancoOk then
  begin
    Erro := 'motivo desconhecido';
    if LoadStringFromFile(ArqErro, ErroLido) then
      Erro := String(ErroLido);
    Log('Configuração do banco falhou: ' + Erro);
    SuppressibleMsgBox('O programa foi instalado, mas a configuração do banco de dados falhou:' + #13#10#13#10 +
      Erro + #13#10#13#10 +
      'Detalhes em ' + PastaDados + '\instalacao.log' + #13#10 +
      'Depois de corrigir, execute este instalador de novo (nada é perdido).',
      mbCriticalError, MB_OK, IDOK);
  end;
end;

{ Desinstalar NÃO apaga o banco, a não ser que a pessoa confirme duas vezes.
  Útil para limpar um PC de teste; na loja a resposta é sempre Não. }
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Dados: String;
begin
  if CurUninstallStep <> usPostUninstall then
    Exit;
  Dados := ExpandConstant('{commonappdata}\SistemaPDV');
  if UninstallSilent or not DirExists(Dados + '\mysql') then
    Exit;
  if MsgBox('Apagar também o BANCO DE DADOS (vendas, produtos, clientes, fiado) e a configuração?' + #13#10#13#10 +
       'No computador da loja responda NÃO: os dados ficam guardados e voltam quando o sistema ' +
       'for instalado de novo.' + #13#10#13#10 +
       'Os backups (' + Dados + '\backup\copias) e os logs nunca são apagados.',
       mbConfirmation, MB_YESNO or MB_DEFBUTTON2) <> IDYES then
    Exit;
  if MsgBox('Tem certeza? Isto não pode ser desfeito.', mbConfirmation, MB_YESNO or MB_DEFBUTTON2) <> IDYES then
    Exit;
  DelTree(Dados + '\mysql', True, True, True);
  DeleteFile(Dados + '\db.properties');
  DeleteFile(Dados + '\backup\backup.cnf');
  DeleteFile(Dados + '\backup\nuvem.txt');
  DeleteFile(Dados + '\backup\backup-casarural.ps1');
end;
