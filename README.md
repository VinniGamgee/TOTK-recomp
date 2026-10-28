# TOTK Recomp · Android

Projeto de port de **The Legend of Zelda: Tears of the Kingdom 1.4.3** para Android ARM64,
partindo da família LAS Recomp. **Gera código C experimental; ainda não executa TOTK.**

A primeira entrega é um APK de diagnóstico para conferir os arquivos do jogo no próprio
celular. O motor de BOTW está disponível como referência de desenvolvimento, fixado em
um commit. Um overlay experimental já carrega os três módulos e gera C; boot e gameplay não foram validados.

Para preparar o runtime experimental e repetir a geração local, veja [docs/PORT.md](docs/PORT.md).
Os arquivos do jogo e o código gerado ficam apenas na máquina de quem compila.

## Usar no celular

1. Abra [Actions → TOTK Android Diagnostics](https://github.com/VinniGamgee/TOTK-recomp/actions/workflows/android-diagnostics.yml).
2. Abra uma execução verde e baixe o artefato **TOTK-Recomp-Diagnostic**. Extraia o ZIP e instale `app-debug.apk`.
3. Use a sua cópia extraída com a atualização **1.4.3 aplicada**. Selecione `exefs` ou a pasta que contém `exefs`.
4. Toque em **Salvar relatório JSON**. O relatório identifica os cinco arquivos esperados e os cabeçalhos dos módulos.

O APK não precisa de root, não acessa a rede e não copia ou modifica os arquivos do jogo.
Somente o relatório é gravado, no destino escolhido. É possível analisar apenas o ExeFS;
não é necessário importar os aproximadamente 17 GB de RomFS nesta etapa.

**“Confere” significa que o arquivo corresponde à referência, não que o jogo já funciona.**
Um hash diferente pode indicar outra versão, alterações ou outra representação do NSO
(por exemplo, descomprimido). RomFS não é validado por este diagnóstico.

## Estrutura

- `android/totk-inspector/`: aplicativo Android de diagnóstico, independente de SDL e Vulkan.
- `profiles/totk-1.4.3.json`: título, versão e hashes de referência, com origem fixada.
- `tools/totk_dump.py`: diagnóstico equivalente para computador, com inventário opcional de imports.
- `vendor/las-runtime/`: submódulo do [BOTWRecompAndroid](https://github.com/mamcjr/BOTWRecompAndroid),
  commit `4572f06716a49207fdcc13d34a45f154644202f3`. Inclui o recompilador, runtime, Vulkan e o app de BOTW.
  Compilar esse submódulo produz o app de BOTW, **não um port de TOTK**.
- [docs/PORT.md](docs/PORT.md): diferenças identificadas, próximos passos e limites da validação.

Para obter também a base de código:

```sh
git clone --recurse-submodules https://github.com/VinniGamgee/TOTK-recomp.git
```

Num checkout já existente: `git submodule update --init --recursive`.

## Diagnóstico no computador

A verificação básica usa apenas Python 3.10+:

```sh
python tools/totk_dump.py /caminho/TOTK --output totk-143-report.json
```

Inventário adicional de imports e relocações de `main`, `subsdk0` e `sdk`:

```sh
python -m venv .venv
# Ative o ambiente virtual antes dos comandos abaixo.
python -m pip install -r tools/requirements-totk.txt
python tools/totk_dump.py /caminho/TOTK --analyze --output totk-143-report.json
```

O código de saída é 0 somente se os cinco arquivos corresponderem à referência e, quando
solicitada, a análise terminar. O inventário não valida o comportamento das funções HLE.
Nenhum código ou recurso do jogo é incluído no JSON.

## Compilar e testar

O diagnóstico requer JDK 17 e Android SDK 36; não requer NDK nem os arquivos do jogo:

```sh
python -m unittest discover -s tests -v
cd android/totk-inspector
./gradlew :app:assembleDebug :app:lintDebug
```

Os testes usam arquivos sintéticos. A compilação do APK não constitui teste de execução do TOTK.

## Créditos

- **peiikai / LAS Recomp**: recompilador e runtime originais.
- **mamcjr / LASRecompAndroid e BOTWRecompAndroid**: base Android e adaptações de BOTW.
- **MacNativeGames contributors**: referência de identificação de TOTK 1.4.3, usada sob MIT;
  ver [third_party/MacNativeGames-LICENSE](third_party/MacNativeGames-LICENSE).
- As dependências da base preservam suas próprias licenças e créditos no submódulo.

Use arquivos da sua própria cópia do jogo. Não envie NSP, ExeFS, RomFS, chaves, saves ou
código recompilado para este repositório ou para os artefatos de CI.
