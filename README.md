# IR Remote BR (PhilipsRemoteSemAds)

Controle remoto por **infravermelho** para Android, **sem anúncios**. Usa o emissor IR do celular
(`ConsumerIrManager`) para controlar TVs, ar-condicionado e ventilador. Controles e botões aprendidos ficam
**só no aparelho** (SQLite local).

> O celular precisa ter emissor IR (vários Xiaomi/Redmi, alguns Huawei/Honor etc.).

## Recursos

- Controle embutido para **Philips (RC6)** e **LG (NEC)** e perfis para Samsung, Sony, Panasonic, AOC, TCL, Philco, Semp, Toshiba/JVC, ar-condicionado (Coolix/Midea) e ventilador.
- **Meus controles**: salvar, buscar, renomear, duplicar e excluir.
- **Configurar botões**: testa os códigos do perfil (varredura 0x00–0xFF) ou um código digitado em hexadecimal e grava o que funcionou para cada tecla.
- **Teste universal** com banco de códigos de teste.
- **Atualização pelo GitHub Releases** com conferência de SHA-256.

## Maturidade dos perfis

| Perfil | Situação |
|---|---|
| Philips RC6, LG NEC | Em uso e validados em TV real |
| Samsung, Sony, Philips RC5, NEC genérico (AOC/TCL/Philco/Semp/Toshiba) | Codificadores corrigidos na 1.3.6 e **testados em JVM** contra vetores do protocolo; falta validar em TV real |
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

## Publicar uma versão (GitHub Actions)

1. Aumente `versionName` **e** `versionCode` em `app/build.gradle` (o app compara o `versionName`).
2. Faça push em `main`: o workflow **Gerar APK** roda os testes, assina o APK, gera o SHA-256 e cria a Release `vX.Y.Z`.
3. Se a Release da versão já existir, nada é sobrescrito; para substituir use *Run workflow → substituir*.

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
- `INTERNET`: apenas para consultar `api.github.com` (última Release) e baixar o APK.
- `REQUEST_INSTALL_PACKAGES`: abrir o instalador do Android na atualização.
- Sem anúncios, sem rastreamento, sem contas. Dados em `allowBackup=false` (não vão para o backup do Google).

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
