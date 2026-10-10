# Controle universal por Wi-Fi — plano incremental

## Objetivo
Adicionar controle de dispositivos pela rede local sem remover nem alterar o caminho atual de infravermelho (IR).

## Nesta primeira etapa
- Adiciona uma tela separada de descoberta por Wi-Fi.
- Pesquisa serviços locais associados a Android TV/Google TV, Google Cast, Samsung e LG webOS.
- Mostra nome, plataforma identificada e endereço quando o serviço é encontrado.
- Mantém o controle IR atual como caminho independente.
- A descoberta não envia comandos nem afirma que um aparelho encontrado já pode ser controlado.

## Suporte específico em desenvolvimento
- **Philips 50PUG6513/7 (SAPHI):** tentativa de conexão manual via IP usando Philips JointSpace API (HTTP na porta 1925, API v6). A API precisa estar exposta/ativada no firmware; a compatibilidade ainda deve ser confirmada na TV real.
- **LG 32LB620B:** tentativa de emparelhamento LG NetCast/ROAP via IP na porta 8080, com chave solicitada na tela da TV. Modelos LG que usem webOS em vez de NetCast precisam de outro protocolo.
- A tela permite inserir o IP, testar a conexão/emparelhar e usar os controles de navegação, volume, canais e mídia quando a conexão for aceita.
- O protocolo IR existente não foi substituído.

## Próximas etapas
1. Compilar e testar o app.
2. Testar Philips JointSpace na TV 50PUG6513/7 e confirmar se a API é exposta por esse firmware SAPHI.
3. Testar emparelhamento NetCast na LG 32LB620B; se a porta 8080 não responder, identificar o protocolo/firmware antes de ativar comandos.
4. Implementar e validar Android TV/Google TV, Samsung Tizen e LG webOS quando aplicável.
5. Criar testes por protocolo e deixar indisponíveis os comandos que não forem suportados.

## Limites importantes
Não existe um protocolo universal de controle por Wi-Fi para todas as marcas. O nome da marca, sozinho, não garante compatibilidade: modelo, sistema da TV, versão do firmware, rede e autorização do usuário também importam. Alguns serviços encontrados por descoberta (por exemplo, Google Cast) não significam que a API de controle remoto está disponível.

## Segurança e privacidade
A descoberta é limitada à rede local. O aplicativo deve solicitar emparelhamento/autorização quando o fabricante exigir e não deve tentar contornar autenticação. Endereços locais descobertos devem permanecer no dispositivo, salvo se o usuário escolher explicitamente exportá-los.
