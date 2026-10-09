# Changelog

## 2.0.0 — versão para a Google Play
### Mudanças de distribuição
- Preparado para a **Google Play** (AAB, versão `play`) com novo `applicationId`: `io.github.tryonbrasil.irremote` (o pacote `com.example.*` é recusado pela Play). Por enquanto os testes seguem por APK (versão `github`), que atualiza por cima da instalação atual.
- **Duas versões (flavors)**: `github` mantém a atualização por APK (`UpdateManager`, `REQUEST_INSTALL_PACKAGES`) e o `applicationId` antigo; `play` não tem auto-update (a Play proíbe) e usa o `applicationId` novo.
- **Atualização por APK endurecida**: sem SHA-256 válido a atualização é cancelada (antes instalava sem conferir) e só aceita APK de `github.com/tryonBrasil/PhilipsRemoteSemAds/releases/download/`.
- `targetSdk`/`compileSdk` 36 (exigência da Play desde 31/08/2026); AGP 8.9.1 e Gradle 8.11.1. Voltar usa `OnBackInvokedCallback` no Android 13+ (o `onBackPressed` deixa de ser chamado no Android 16).
- R8/shrinkResources ligados no release (`proguard-rules.pro`).

### Anúncios, Premium e privacidade
- IDs do AdMob vêm do build: **debug usa IDs de teste; release exige IDs reais e é bloqueado sem eles**.
- Consentimento **UMP** (GDPR) antes de inicializar o AdMob, com botão *Opções de privacidade dos anúncios* quando exigido.
- Billing: reconexão limitada (5 tentativas com espera crescente, antes era um loop infinito de 1,5 s), `endConnection()` e banners destruídos no `onDestroy`, `BillingClient` com contexto da aplicação.
- Corrigido o aviso "Premium ativado" que aparecia a cada abertura do app.
- Texto do Premium corrigido: só promete o que existe (sem anúncios + controles ilimitados). Removidas promessas de "mais códigos" e "recursos avançados".
- Novos `PRIVACY.md` (política de privacidade) e link dentro do app; `PLAY_CHECKLIST.md`.

### Visual dos controles (TV, ar-condicionado e ventilador)
- **TV:** cabeçalho único (nome, perfil, status do emissor IR e liga/desliga), ações *Trocar*, *Editar* e *Meus controles*, abas **Principal / Números / Mídia**, D-pad circular com OK central, volume e canais em bastões com o mudo no meio e teclas coloridas redondas. Os ícones agora são vetoriais (sem emojis). Aviso no topo quando o celular não tem emissor IR.
- **Teclado numérico:** número grande com as letras pequenas embaixo.
- **Corrigido:** o botão "TV" enviava o mesmo comando de "Fonte"; foi removido. *Info* e *Netflix/Smart* foram para a aba Mídia.
- **Ar-condicionado:** liga/desliga único (verde = ligado), temperatura em destaque com botões redondos, modos com ícone e cor por tipo (frio, quente, seco, ventilar, auto), textos em português sem caixa alta.
- **Ventilador:** liga/desliga grande e funções (oscilação, velocidade, timer, noturno) em cartões com ícone.
- **Tela inicial, seletor e conta:** botões largos com ícone, cartões em linha (controle ativo, meus controles, controle universal), TVs prontas como cartões lado a lado e acesso a *Conta e privacidade* por um ícone no topo. Corrigido o texto "sem anúncios" do seletor, que não valia mais.
- **Meus controles:** cartões sem altura fixa (nada mais é cortado), ícone da categoria (TV, ar-condicionado, ventilador), barra de progresso dos botões configurados, ação principal *Abrir* e ações *Auto, Testar, Duplicar, Nome e Excluir* com ícone; estado vazio sem emojis.
- **Configurar botões:** barra de progresso, alturas que se ajustam ao conteúdo, lista com altura proporcional à tela e botões do diálogo em caixa normal.
- Botões maiores (mínimo recomendado de 48 dp na maioria), descrição de acessibilidade nos botões de ícone e coluna central de no máximo 420 dp em telas largas.

### Corrigido
- `IrCatalog` podia ler o `ir_catalog.json` pela metade.
- Possível crash ao abrir diálogo depois que a tela foi fechada (threads de rede chamando a UI).
- Sinais IR dos bancos online passam por limites de frequência (15–60 kHz) e tamanho (2000 durações, 1 s cada); respostas de rede grandes demais são recusadas.

### CI
- `ci.yml` (todas as branches e PRs): `lintDebug`, testes e APK de debug com o Gradle Wrapper.
- `build-apk.yml` (push em `main`): versão `github` — testes, APK assinado, `app-release.apk.sha256` e Release; não sobrescreve Release existente (opção *substituir*) e ignora commits só de documentação.
- `build-aab.yml` (tag `vX.Y.Z` enviada por você, ou manual): versão `play` — confere a versão da tag, roda testes, exige secrets, gera o AAB assinado e o `mapping.txt`.
- Removidos `android.enableJetifier` e o `.zip` que estava dentro do repositório; `*.zip` no `.gitignore`.

## 1.4.0 – 1.7.5
Histórico não registrado na época. Pelo código, entraram nesse período: banco de ar-condicionado SmartIR, banco online Flipper-IRDB, catálogo local de marcas, configuração automática de botões, exportar/importar backup e anúncios (AdMob) com compra Premium (Google Play Billing).

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
