# IR Remote BR (PhilipsRemoteSemAds)

Controle remoto por **infravermelho** para Android, com anúncios intersticiais Start.io para usuários gratuitos e opção Premium sem anúncios. Usa o emissor IR do celular
(`ConsumerIrManager`) para controlar TVs, ar-condicionado e ventilador. Controles e botões aprendidos ficam
**só no aparelho** (SQLite local).

> O celular precisa ter emissor IR (vários Xiaomi/Redmi, alguns Huawei/Honor etc.).

## Recursos

- Configuração automática completa para **Philips (RC6)** e **LG (NEC)**; tabelas candidatas para Samsung/Sony com aviso para testar no aparelho real. Outros perfis (AOC, TCL, Philco, Semp, Toshiba/JVC, Panasonic, ar-condicionado e ventilador genérico) usam configuração guiada quando não há tabela segura.
- **Meus controles**: salvar, buscar, renomear, duplicar e excluir.
- **Configurar botões**: testa os códigos do perfil (varredura 0x00–0xFF) ou um código digitado em hexadecimal e grava o que funcionou para cada tecla.
- **Teste universal** com banco de códigos de teste.
- **Atualização pelo GitHub Releases** com conferência de SHA-256.

## Maturidade dos perfis

| Perfil | Situação |
|---|---|
| Philips RC6, LG NEC | Em uso e validados em TV real |
| Samsung | Codificador testado em JVM com endereço TV E0E0 e comandos comuns; tabela automática candidata, ainda precisa validar em TV real |
| Sony | Codificador SIRC testado em JVM; tabela automática candidata, ainda precisa validar em TV real |
| Philips RC5 | Codificador testado em JVM; configuração automática desativada até validar o mapeamento de funções |
| NEC genérico (AOC/TCL/Philco/Semp/Toshiba) | Varredura guiada por endereço/comando; os códigos variam por modelo |
| AC Coolix / "Midea" | Quadro Coolix corrigido (B2 4D 7B 84 E0 1F…); falta validar em um AR real |
| Panasonic | **Não verificado**: a ordem de bits do ID do fabricante pode estar invertida |
| Ventilador | Códigos brutos genéricos; compatibilidade varia por modelo |

## Instalar

Baixe o `app-release.apk` na aba **Releases** e instale. O app se atualiza sozinho: ao abrir, consulta a última
Release; o download automático só ocorre em rede sem franquia e a instalação sempre pede sua confirmação.
Confira o hash com o arquivo `app-release.apk.sha256` da Release.

## Compilar

```bash
./gradlew assembleDebug          # APK de teste em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # testes dos codificadores IR e das versões
./gradlew lintDebug
```

Requer JDK 17. No Windows use `GERAR_APK.bat` (debug) ou `gradlew.bat`.

## Versionamento e publicação automática

**Não precisa alterar a versão manualmente.** Toda vez que houver um push na branch `main`, o workflow **Gerar APK**:
1. Incrementa automaticamente o último número da versão (por exemplo, `2.0.4` → `2.0.5`) e aumenta o `versionCode`.
2. Registra a nova versão em `app/build.gradle` num commit automático.
3. Compila e verifica o APK assinado, calcula o SHA-256 e publica a Release correspondente (`vX.Y.Z`).

O versionamento automático pressupõe que o push tenha permissão para gravar na branch `main`. Se o workflow falhar, abra **Actions → Gerar APK** e confira a etapa que falhou. Para republicar exatamente a mesma versão sem incrementar, use **Run workflow** e marque `replace_existing` somente quando realmente quiser substituir os arquivos da Release.

Secrets necessários (Settings → Secrets and variables → Actions):

| Secret | Conteúdo |
|---|---|
| `KEYSTORE_B64` | keystore `.jks` em base64 (`base64 -w0 ir-remote-release.jks`) |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | alias da chave |
| `KEY_PASSWORD` | senha da chave |

**Faça backup do keystore**: sem ele não é possível publicar atualizações para quem já instalou.
Pull requests e outras branches usam o workflow **CI** (compila, testa e roda lint, sem secrets).

## Privacidade e permissões

- `TRANSMIT_IR`: enviar os sinais.
- `INTERNET`: necessária para atualizações, anúncios Start.io e recursos online do Premium (Firebase e servidor de pagamento).
- `REQUEST_INSTALL_PACKAGES`: abrir o instalador do Android na atualização.
- O app exibe anúncios intersticiais Start.io para usuários gratuitos; usuários Premium não devem receber anúncios. Consulte a política de privacidade publicada e os termos da Start.io para detalhes sobre dados tratados pelos serviços integrados.
- A autenticação Google/Firebase é usada nos recursos Premium. Dados locais do app usam `allowBackup=false` (não vão para o backup do Google).

## Estrutura

| Arquivo | Papel |
|---|---|
| `IrEncoder` | Codificadores IR puros (NEC, Samsung32, Sony SIRC, RC5, RC6, Coolix, Panasonic) — **testados** |
| `IrPerfilTeste` | Perfis, candidatos, varredura, envio de códigos salvos, banco de códigos de teste |
| `RemoteKeys` | Teclas do app, chaves gravadas e tabela da LG |
| `ControleStorage` / `ControleDatabase` | Persistência SQLite (com migração do formato antigo) |
| `UpdateManager` / `VersionUtil` | Atualização pelo GitHub Releases |
| `MainActivity` | Telas |

## Limitações conhecidas

- A interface é montada em código (sem XML de layout) e em português fixo.
- `applicationId` ainda é `com.example.philipsremote`; trocá-lo exige reinstalar o app (os controles não migram sozinhos).
- Não há exportar/importar controles.
