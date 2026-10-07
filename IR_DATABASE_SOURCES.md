# Fontes da base de controles IR

O aplicativo consulta bases públicas de códigos IR somente quando necessário.

## SmartIR

- Repositório: https://github.com/smartHomeHub/SmartIR
- Licença: MIT
- Uso no app: modelos de ar-condicionado e seus estados IR em formato SmartIR/Broadlink Base64.

## SmartIR Code Aggregator

- Repositório: https://github.com/tonyperkins/smartir-code-aggregator
- Licença: MIT
- Uso no app: índice de fabricantes/modelos para localizar rapidamente os códigos SmartIR.

## Flipper-IRDB

- Repositório: https://github.com/Lucaslhm/Flipper-IRDB
- Uso no app: controles RAW no formato .ir, principalmente ventiladores e outros dispositivos.
- Novas contribuições do repositório são publicadas sob CC0; arquivos históricos podem ter condições de autoria diferentes, portanto o app não assume que todo arquivo histórico tenha a mesma licença.

## Observação técnica

Códigos de ar-condicionado normalmente representam o estado completo do aparelho (modo, temperatura, ventilação etc.). Por isso o aplicativo monta e envia um código completo para cada estado, em vez de tratar temperatura e modo como simples comandos independentes.
