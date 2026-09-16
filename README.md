## Problema:
Na empresa que trabalho, existe um programa de ponto em Java que é responsável por ler o crachá do funcionário, montar o registro de ponto, guardar localmente no computador e também inserir no banco de dados. Entretanto, o último passo, muitas vezes, não ocorria pois o computador poderia não estar conectado na WAN via VPN. Então, o registro de ponto ficava salvo apenas no computador do usuário.

## Solução:
Criar um sincronizador de ponto que tenta, periodicamente, trazer os registros ainda não inseridos no banco de dados.

Neste repositório, está minha implementação de um componente chamado Sincronizador, em Java.

### Detalhes:
