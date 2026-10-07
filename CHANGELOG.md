# Changelog

## 1.3.6
### Corrigido
- **Sony**: o bit era codificado no espaço (o SIRC codifica na marca) e o intervalo entre quadros virava uma marca de 10 ms.
- **Samsung**: o 2º byte era `~endereço`; no Samsung32 o endereço é repetido.
- **Philips RC5**: enviava 15 bits e com polaridade invertida (agora 14 bits, Manchester correto).
- **AC Coolix/"Midea"**: quadro refeito (bytes intercalados com o inverso, MSB primeiro, 2 quadros com header).
- **Ventilador**: comando 5 tinha número ímpar de durações (o intervalo final virava marca); teclas aprendidas passam a ser usadas; estado "ventilador" vazava para o controle de TV (dígitos 1–5 enviavam códigos de ventilador).
- **Códigos salvos de teste**: separador de linha gravado como `\n` literal; com 2+ itens, tocar num deles travava o app.
- Textos com `\n` literal nas telas de modelo e de teste IR.
- Descrição do controle salvo ficava sempre vazia.
- Renomear/excluir o controle ativo comparava objetos por referência (o excluído continuava "ativo"; o nome antigo continuava na tela).
- Seletor de TV sumia assim que existia qualquer controle salvo (sem controle ativo não havia como escolher Philips/LG).
- Teste manual em hexadecimal sempre usava NEC; agora usa o perfil escolhido e o código digitado pode ser salvo.
- **Anterior** do teste passa a retransmitir o candidato anterior (antes salvava um código nunca enviado).
- Listas de marca/modelo não apareciam em ROMs que seguem o AOSP (`setMessage` + `setItems`).
- LG: `SETTINGS` repetia o `MENU` (agora Q.MENU) e `CC` enviava o dígito 0.
- "telemóvel" → "celular".
- Android 15 (targetSdk 35): conteúdo respeita as barras do sistema.

### Atualizações automáticas
- Intervalo mínimo de 15 min entre consultas (antes era zerado a cada abertura); sem `?t=` na URL.
- Download automático só em rede sem franquia; não baixa de novo a mesma versão por 24 h depois de abrir o instalador.
- APKs antigos são apagados; conferência de SHA-256 (campo `digest` da Release); receiver compatível com Android 13+.
- Aviso de permissão de instalação não reaparece a cada abertura depois de recusado.

### Melhorias
- Codificadores IR extraídos para `IrEncoder` (puro, sem Android) com **testes unitários**.
- Configurar botões: varredura 0x00–0xFF, entrada hexadecimal, dígitos e CC aprendíveis.
- Armazenamento: consultas únicas, erros registrados no log, `buscar(id)`, `salvar` devolve o id.
- Gradle Wrapper oficial (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`).
- CI separado (`ci.yml`), release com SHA-256, cache do Gradle, `concurrency`, assinatura sem keystore fixo em disco.
- README, CHANGELOG, `.gitignore`, `strings.xml`, dependabot.

## 1.3.5
- Versão enviada para análise (SQLite, ventilador universal, busca/duplicar controles).
