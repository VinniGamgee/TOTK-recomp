# TOTK 1.4.3: estado do port

## O que está implementado

- Base LAS/BOTW Android disponível como submódulo fixado em
  `4572f06716a49207fdcc13d34a45f154644202f3`.
- Perfil de TOTK `0100F2C0115B6000`, atualização 720896 / 1.4.3. Os hashes vêm de
  `MacNativeGames@ff63a162b35d8b486b941191785d8e957c1f5df2`,
  `games/totk/assets/expected-v1.4.3.json`, blob `813bd7968b67fb69f279daee532ee97c4182596b`.
- Diagnóstico no Android usando Storage Access Framework e leitura sequencial dos arquivos.
- Diagnóstico em Python e inventário opcional de imports/relocações.
- CI para testes sem arquivos de jogo, compilação e lint do APK de diagnóstico.

Não há boot de TOTK implementado ou confirmado. Nenhum dump real foi analisado nesta fase.
Não houve teste em POCO F5. A execução de BOTW relatada pelo upstream não é uma validação de TOTK.

## Diferenças verificadas no código

| Área da base BOTW | Situação observada | Trabalho necessário para TOTK |
| --- | --- | --- |
| `runtime/loader.c` | `loader_load` carrega apenas main e sdk; identificação lista builds de BOTW | Identificar os builds reais e carregar os módulos requeridos por TOTK |
| `runtime/gamelib.h`, `tools/gamelib_entry.c` | ABI 1 expõe duas tabelas de funções | Revisar ABI, gerador e consumidor juntos para suportar subsdk0 |
| `runtime/dispatch.c` | Duas tabelas; inicialização, lookup e profiler assumem esse limite | Suportar todas as tabelas, inclusive rebase e reinicialização |
| `runtime/gamebuild.c`, `android/build_gamelib.sh` | Geram main e sdk, endereços fixos separados por 64 MiB | Medir imagens e reservar layout sem sobreposição; gerar os três módulos |
| `runtime/main.c` | Construtores/finalizadores referenciam índices específicos de main/sdk | Conferir a sequência de inicialização e finalização do SDK de TOTK |
| `tools/patches.py` | Patches selecionados por build ID de BOTW | Manter patches de outro jogo fora do caminho de TOTK; criar patches verificados |
| `runtime/hle/framepace.c`, `widescreen.c` | Condições e offsets específicos de BOTW | Desabilitar no futuro alvo TOTK até portar e validar os hooks |
| `tools/nso.py`, `runtime/loader.c` | Já tratam DT_RELR e DT_GNU_HASH | Exercitar com os módulos de TOTK, sem assumir equivalência de SDK |
| `runtime/services`, `runtime/nvn` | Implementações feitas para os jogos anteriores | Conferir imports, ABI, IPC, áudio, NVN e shaders usados por TOTK |

A referência MacNativeGames registra três módulos de TOTK 1.4.3, mudanças no SDK,
relocações RELR e áudio REV15 em `docs/v1.4.3-port.md`. O código Apple executa ARM64
reescrito e usa Metal. Esta base recompila para C e usa Vulkan: não há substituição direta
dos binários ou cópia universal de patches entre os dois mecanismos.

## Próximo insumo e próximos marcos

1. Analisar os cinco arquivos do ExeFS 1.4.3: `main`, `main.npdm`, `rtld`, `sdk`, `subsdk0`.
   O JSON Android fornece identidade e tamanho das imagens; ele não contém os módulos.
2. Fazer o inventário completo com `tools/totk_dump.py --analyze`. Para modificar e validar
   a geração de código, será necessário acesso local ao ExeFS; os hashes sozinhos não bastam.
3. Implementar suporte consistente aos três módulos, layout de memória e nova ABI de biblioteca;
   testar carregamento, relocações e construtores antes do primeiro `nnMain`.
4. Integrar o alvo TOTK ao APK do runtime e obter logs reais de inicialização.
5. Corrigir os caminhos de sistema e gráficos encontrados nos testes. Confirmar imagem,
   áudio, controles, salvamento e carregamento no dispositivo.

O RomFS extraído deve corresponder à base mais atualização cumulativa 1.4.3. O diagnóstico
atual só registra a presença de uma pasta com esse nome, quando a pasta-pai é selecionada;
não testa completude, hashes ou correspondência do RomFS.

## Verificações desta entrega

- Testes Python: inputs sintéticos válidos, corrupção, módulo ausente, NSO truncado,
  segmento fora do arquivo e seleção da pasta-pai.
- Teste Java do núcleo usado pelo APK: NSO/NPDM, hash incorreto, truncamento, limites e cancelamento.
- Workflow: `assembleDebug` e `lintDebug` do APK de diagnóstico.
- Interface e acesso a provedores de arquivos ainda precisam de teste em um Android real.
