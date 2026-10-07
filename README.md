# IR Remote BR

Aplicativo Android de controle remoto infravermelho, sem anúncios.

## Build

O projeto usa Android Gradle Plugin 8.6.1 e Gradle 8.7, com Java 17. O CI usa Gradle instalado pelo GitHub Actions e não depende do wrapper improvisado do projeto.

### APK de teste
Abra **Actions → CI → Artifacts → PhilipsRemoteSemAds-debug**.

### Release
Crie uma tag no formato `vX.Y.Z`. O workflow de release gera o APK assinado e um arquivo SHA-256.

Secrets necessários para release:
- `KEYSTORE_B64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

A chave é materializada somente em `RUNNER_TEMP` e não deve ser commitada.

## Atualizações

O aplicativo consulta o GitHub com cooldown persistente de 6 horas. Uma atualização encontrada **não é baixada automaticamente**: o usuário confirma o download. O download é limitado a Wi-Fi e o Android continua validando a assinatura do APK durante a instalação.

## IR

Os encoders de NEC, Samsung, Sony SIRC, RC5, RC6, Panasonic, Coolix/Midea e o perfil universal de ventilador ficam em `IrPerfilTeste`.

Antes de alterar um encoder, adicione/atualize um teste determinístico para o waveform esperado e depois valide em aparelho real.

## Permissões

- `TRANSMIT_IR`: necessária para enviar sinais pelo emissor IR.
- `INTERNET`: usada somente para consultar releases e baixar atualizações confirmadas pelo usuário.
- `REQUEST_INSTALL_PACKAGES`: necessária para instalar uma atualização APK baixada fora da Play Store.

Os controles são armazenados localmente no aparelho.
