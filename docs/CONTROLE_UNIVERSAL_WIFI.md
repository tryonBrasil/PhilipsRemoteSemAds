# Controle universal por Wi-Fi — plano incremental

## Objetivo
Adicionar controle de dispositivos pela rede local sem remover nem alterar o caminho atual de infravermelho (IR).

## Nesta primeira etapa
- Adiciona uma tela separada de descoberta por Wi-Fi.
- Pesquisa serviços locais associados a Android TV/Google TV, Google Cast, Samsung e LG webOS.
- Mostra nome, plataforma identificada e endereço quando o serviço é encontrado.
- Mantém o controle IR atual como caminho independente.
- A descoberta não envia comandos nem afirma que um aparelho encontrado já pode ser controlado.

## Próximas etapas
1. Compilar e testar a descoberta em uma rede local real.
2. Implementar e validar emparelhamento/comandos Android TV e Google TV.
3. Implementar protocolos de Samsung Tizen e LG webOS em módulos separados.
4. Pesquisar protocolos documentados para Roku, Sony, TCL, Hisense, Philips e demais plataformas.
5. Criar testes por protocolo e deixar indisponíveis os comandos que não forem suportados.

## Limites importantes
Não existe um protocolo universal de controle por Wi-Fi para todas as marcas. O nome da marca, sozinho, não garante compatibilidade: modelo, sistema da TV, versão do firmware, rede e autorização do usuário também importam. Alguns serviços encontrados por descoberta (por exemplo, Google Cast) não significam que a API de controle remoto está disponível.

## Segurança e privacidade
A descoberta é limitada à rede local. O aplicativo deve solicitar emparelhamento/autorização quando o fabricante exigir e não deve tentar contornar autenticação. Endereços locais descobertos devem permanecer no dispositivo, salvo se o usuário escolher explicitamente exportá-los.
