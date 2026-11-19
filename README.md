# Geometry Dash Archive Compiler

App Android (Java, sem WebView, sem dependencias externas) que recria o editor do Geometry Dash e integra o SPWN ORIGINAL.

## IMPORTANTE: como gerar o APK (leia)
Este zip e um PROJETO-FONTE. Ele NAO contem `classes.dex` pronto: `.java` precisa ser compilado por um Android SDK (javac + d8),
e o APKTool M nao compila Java (ele so remonta smali/recursos). Alem disso o compilador SPWN (Rust) precisa ser compilado
para Android (NDK), o que exige internet e toolchain. Por isso o caminho que funciona no celular e:

### Opcao A - GitHub Actions (recomendado, so pelo navegador do celular)
1. Crie um repositorio no GitHub e envie o conteudo deste zip (pasta `.github` inclusa).
2. Aba **Actions** > "Build GD Archive Compiler APK" > Run workflow.
3. Baixe o artefato `GeometryDashArchiveCompiler-apk` (APK debug assinado, instalavel).
   O workflow tambem clona `Spu7Nix/SPWN-language`, compila o binario `spwn` para arm64 e o empacota como `libspwn.so`.
   Esse passo esta com `continue-on-error`: se a compilacao Rust falhar, o APK sai sem SPWN e o app avisa
   (nada e simulado). Veja o log do passo "Build SPWN original".

### Opcao B - Android Studio / AIDE
Abra a pasta como projeto Gradle. Para ter o SPWN, coloque o binario compilado em
`app/src/main/jniLibs/arm64-v8a/libspwn.so` e as bibliotecas do SPWN em `app/src/main/assets/spwn_libraries/`.

## Como o SPWN e integrado
O app executa o binario original (`spwn build <arquivo>`) a partir de `nativeLibraryDir`. Nenhuma linguagem foi recriada.
Argumentos extras ficam em Menu > Configuracoes (padrao `--no-level`, para imprimir o level string em vez de gravar no jogo).
O app procura na saida um token que comece com `H4sI` e oferece "Importar resultado". CONFIRME os flags da sua versao do SPWN.

## Recursos
- Editor em Canvas: grade, snap (30/15/7.5/off), zoom por pinca, mao, multi-selecao, mover/rotacionar/escalar/duplicar/apagar,
  grupos (chave 57), cor (21), layers (20), Z (25), paleta, propriedades (todas as chaves, editaveis), desfazer/refazer, preview.
- Script SPWN: realce, sugestoes, barra de simbolos, marcacao de linhas com erro, importar/salvar .spwn.
- Decompilador: Base64 + gzip/zlib, CCLocalLevels.dat (XOR 11) ou plist XML; saida JSON estruturada (editavel e reaplicavel)
  e reconstrucao SPWN-like (NAO e o codigo original). Campos desconhecidos sao preservados.
- Triggers: inspetor, validacao de referencias, grafo/cadeias de Spawn, auto organizador (previa + backup + desfazer).
- Arquivos: Storage Access Framework, projetos .gdacproj, backups automaticos, comparar e restaurar. Nunca sobrescreve originais.
- IA (opcional): chave de API propria em Configuracoes.

## Limites conhecidos (honestos)
- Nao foi compilado nem testado aqui (sem Android SDK no ambiente de geracao). Podem existir erros de compilacao a corrigir.
- CCLocalLevels.dat criptografado com AES (outras plataformas) nao e suportado; so XOR/plist.
- Tabelas de IDs/chaves de triggers vieram de memoria: confira no jogo. Chaves desconhecidas nunca sao perdidas.
- Gravar de volta no CCLocalLevels.dat nao esta implementado; exporte o level string e importe/cole no jogo.
- Compilar arm64 apenas.
