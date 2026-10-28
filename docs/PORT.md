# TOTK 1.4.3: estado experimental

Ainda não há APK jogável nem boot confirmado. O APK distribuído pelo CI continua sendo
somente o diagnóstico. O overlay de runtime é desenvolvimento em andamento.

## Implementação

`patches/totk143.patch` adapta a base LAS/BOTW Android fixada em
`4572f06716a49207fdcc13d34a45f154644202f3`. `tools/prepare_runtime.py` aplica a mudança
em uma cópia separada; o submódulo original permanece intacto.

- Identificação de TOTK 1.4.3 e verificação SHA-256 dos três módulos antes da geração.
- Carregamento de main, sdk e subsdk0, com limites de imagem sem sobreposição.
- ABI 2 da biblioteca `totkgame`, terceira tabela no dispatch e rebase Android.
- Geração e fila de compilação dos três módulos, inclusive no builder Android.
- Patches de BOTW removidos; frame pacing e widescreen com offsets de BOTW desativados.
- Import sem implementação interrompe explicitamente, em vez de retornar sucesso falso.
- Sequência proposta de construtores: main, subsdk0, sdk. Ainda não foi executada.
- Configuração inicial do aplicativo separado; launcher, importação e saves ainda exigem revisão.

| Módulo | Base canônica | Funções geradas | Arquivos de código | Instruções não suportadas |
| --- | --- | ---: | ---: | ---: |
| main | 0x7000000000 | 228238 | 1464 | 12 |
| sdk | 0x7004000000 | 40516 | 166 | 1 |
| subsdk0 | 0x7005000000 | 13936 | 119 | 0 |

Os contadores são a descoberta do gerador, não uma prova de cobertura ou correção.
As instruções não suportadas viram traps: podem interromper o jogo se forem alcançadas.

## Evidência local com ExeFS real

Os cinco arquivos enviados correspondem ao perfil de referência 1.4.3.
A geração C terminou para os três módulos (282690 funções; 1749 chunks).
O teste `tests/loader_probe.c`, usando o loader de produção adaptado, carregou os três
módulos e conferiu **517515 relocações relativas** contra pares produzidos pelo leitor
Python independente. O harness usa stubs para imports: não valida serviços, dispatch,
construtores ou instruções do jogo. Foram compilados objetos AArch64 Linux de uma amostra
(`*_000.c` de cada módulo), usando Clang via Zig 0.14.1. Isso não é compilação Android NDK,
nem validação de todos os arquivos, link da biblioteca ou build do APK do runtime.

Os seis testes Python de diagnóstico passaram. O patch aplica sobre a revisão fixada.
Nenhum ExeFS, RomFS, par de relocação ou código derivado do jogo integra o repositório ou CI.

## Bloqueios conhecidos

O inventário encontrou oito imports de main sem implementação na base:
`glslcCompile`, `glslcCompilePreSpecialized`, `glslcCompileSpecialized`, `glslcFinalize`,
`glslcGetDefaultOptions`, `glslcGetVersion`, `glslcInitialize`, `glslcSetAllocator`.
Resolver nomes dos outros imports não comprova compatibilidade de comportamento.

Ainda faltam validar as 13 instruções não suportadas, compilar/linkar todo o código com NDK,
portar as diferenças de SDK/IPC/áudio (incluindo REV15), confirmar a ABI NVN e implementar
os caminhos de shaders. A seleção NVN herdada ainda não foi portada. O frontend conserva
fluxos de BOTW (saves, DLC, textos e heurísticas de RomFS): não deve ser distribuído como
um port pronto. A contagem de arquivos RomFS no overlay é apenas referência, sem garantia
de completude. Nenhum RomFS foi analisado e nenhum teste em POCO F5 foi realizado.

## Reproduzir localmente

Python 3.11 ou superior, Git e ferramentas de compilação compatíveis são necessários.

```sh
git submodule update --init
python -m pip install -r tools/requirements-generator.txt
python tools/prepare_runtime.py
python tools/generate_totk.py /caminho/exefs
python tools/relative_pairs.py /caminho/exefs work/relative-pairs.bin
clang -O1 -Iwork/runtime/runtime tests/loader_probe.c work/runtime/runtime/loader.c -o work/loader_probe
work/loader_probe /caminho/exefs work/relative-pairs.bin
```

O teste do loader requer Linux x86-64, endereços fixos livres e Clang recente (builtins
vetoriais do upstream; GCC não é suficiente). Não executa código ARM64. O arquivo de pares
é criado exclusivamente: remova-o explicitamente antes de repetir. O preparador recusa
sobrescrever uma pasta existente. Os arquivos locais ficam em `work/`, ignorada pelo Git.
A geração pode consumir vários GB e não produz um APK automaticamente.

## Referência

O perfil vem de `MacNativeGames@ff63a162b35d8b486b941191785d8e957c1f5df2`,
`games/totk/assets/expected-v1.4.3.json` (blob `813bd7968b67fb69f279daee532ee97c4182596b`).
A sequência de inicialização é baseada no loader TOTK dessa referência, com licença MIT
em `third_party/`. O runtime Apple usa ARM64 reescrito e Metal; esta base gera C e usa Vulkan.
A referência não comprova funcionamento deste port Android.
