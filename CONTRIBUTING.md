# Contribuir

O alvo é TOTK 1.4.3 para Android. Consulte `docs/PORT.md` antes de alterar o runtime.
Não declare suporte a gameplay com base apenas em compilação, hashes ou testes sintéticos.

Não adicione arquivos do jogo, chaves, saves, fontes da Nintendo ou código gerado ao Git.
O CI só usa dados sintéticos e não recebe dumps.

`vendor/las-runtime` é uma referência fixada de BOTW, não o runtime de TOTK pronto.
Mudanças no port devem ser explícitas, revisáveis e acompanhadas de atualização do estado
do projeto. Não use offsets de BOTW em TOTK por semelhança de número de versão.

Rode `python -m unittest discover -s tests -v` e compile/lint o APK de diagnóstico quando
modificar seu código. Testes de runtime precisam distinguir resultados sintéticos de
execução real do jogo no dispositivo.
