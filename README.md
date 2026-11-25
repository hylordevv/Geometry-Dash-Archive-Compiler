# GD Archive Compiler

Editor de níveis do Geometry Dash para Android, feito em Java. Tem mapa visual no estilo do editor do GD, código legível, importação e exportação de vários formatos e IA integrada.

## Recursos

- **Mapa estilo GD:** fundo azul, grade, chão, paleta de objetos. Você coloca, move, gira, espelha, duplica e apaga objetos tocando na tela.
- **Código legível:** uma linha por objeto, com realce de sintaxe, números de linha, desfazer/refazer e buscar/substituir.
- **Mapa e código sincronizados:** o que você faz num aparece no outro.
- **Importa:** `.gdlvl`, `.gmd`, `.lvl`, `.gmd2` (zip), saves `.dat` (XOR + gzip, com escolha do nível), base64+gzip (`H4sI...`), gzip/zlib cru, nível decodificado (`kS38,...;`), `.gdc` e `.spwn`.
- **Exporta:** `.gdlvl`, `.gmd`, `.lvl`, texto codificado, `.gdc` e `.spwn`.
- **Ferramentas:** base64, base64 URL-safe, gzip e zlib.
- **IA:** OpenRouter, OpenAI, Claude, Gemini, Groq, DeepSeek, Mistral, xAI, Together, Ollama (local) e qualquer API compatível com OpenAI.
- **Tema** claro e escuro.

## Sintaxe do código

Uma linha por objeto:

```
Nome(opção) pos(x,y) modificadores...
```

Exemplos:

```
Bloco pos(15,15)
Espinho pos(45,15) rot(90)
Trigger(Cor) pos(300,15) rgb(255,0,0) tempo(0.5) alvocor(1) zlayer(3) zorder(2)
Objeto(1234) pos(60,15)
```

- `pos(x,y)`: posição. Um terceiro valor, `pos(x,y,z)`, vira `zlayer`.
- A grade tem 30 unidades por bloco. O centro do primeiro bloco no chão é `pos(15,15)`.
- `Objeto(ID)` serve para qualquer ID sem nome.
- `P<n>(valor)` serve para propriedades sem nome (ex.: `P120(1)`).
- Comentários começam com `//` ou `#`.
- A linha `header "..."` guarda as configurações do nível (cores, modo etc.). Não apague.
- O formato antigo do SPWN também funciona: `$.add(obj{ OBJ_ID: 1, X: 15, Y: 15 })`.

### Objetos com nome

`Bloco`, `Laje`, `Espinho`, `EspinhoPequeno`, `PadAmarelo`, `PadAzul`, `PadRosa`, `OrbAmarelo`, `OrbAzul`, `OrbRosa`, `GravidadeNormal`, `GravidadeInvertida`, `PortalCubo`, `PortalNave`, `PortalBola`, `PortalUFO`, `PortalWave`, `PortalRobo`, `PortalAranha`, `Velocidade05`, `Velocidade1`, `Velocidade2`, `Velocidade3`, `Velocidade4`

Triggers: `Trigger(Mover)`, `Trigger(Cor)`, `Trigger(Alpha)`, `Trigger(Toggle)`, `Trigger(Spawn)`, `Trigger(Girar)`, `Trigger(Seguir)`, `Trigger(Pulso)`, `Trigger(Parar)`

### Modificadores

| Modificador | Função |
|---|---|
| `pos(x,y)` | posição |
| `rot(graus)` | rotação |
| `flipx`, `flipy` | espelhar |
| `escala(n)` | escala |
| `cor(n)`, `cor2(n)`, `alvocor(n)` | canais de cor |
| `rgb(r,g,b)` | cor do trigger |
| `tempo(s)` | duração |
| `alvo(grupo)` | grupo alvo |
| `grupos(1,2,3)` | grupos do objeto |
| `mover(x,y)` | movimento do trigger Mover |
| `easing(n)` | easing |
| `opacidade(n)` | opacidade |
| `zlayer(n)`, `zorder(n)` | camada e ordem Z |
| `camada1(n)`, `camada2(n)` | camadas do editor |
| `toque(sim)` | touch triggered |

## Como usar

- **Construir:** escolha um objeto na paleta e toque no mapa. Arraste para mover a câmera e use dois dedos para dar zoom.
- **Editar:** toque num objeto para selecionar e arraste para mover. Os botões fazem Propriedades, Duplicar, Girar 90, Espelhar e Excluir.
- **Excluir:** toque no objeto.
- **Código:** edite o texto. Ao sair da aba, o código é aplicado no mapa. Se houver erro, o app mostra a linha.
- **IA:** configure o provedor e a chave em *Menu > Configurar IA*. Marque "Enviar o código do editor" para dar contexto e use "Inserir no editor" para trazer a resposta.
- **Menu:** novo nível, importar, exportar, ferramentas, centralizar mapa, tema e formatos aceitos.

## Compilar o APK (Acode, Alpine 32-bit)

```
apk add bash unzip
cd ~ && unzip -o /sdcard/Download/GDAC.zip && cd GDAC && bash build.sh
```

O `build.sh` instala o JDK 8 e o `android-build-tools`, baixa o `android.jar` (API 30) e o `r8.jar`, compila e assina. O APK sai em `out/GDAC.apk` e é copiado para `Download/GDAC.apk`.

Se travar na assinatura:

```
JAVA_TOOL_OPTIONS="-Djava.security.egd=file:/dev/urandom" bash build.sh
```

## Estrutura

```
GDAC/
├── AndroidManifest.xml
├── build.sh
├── res/drawable/ic_launcher.xml
└── src/com/hylor/gdac/
    ├── MainActivity.java   interface, abas, importar/exportar, IA
    ├── LevelView.java      mapa estilo GD
    ├── CodeEditor.java     editor com realce e números de linha
    ├── Readable.java       código legível <-> nível
    ├── Codec.java          formatos do GD (gmd, lvl, dat, gzip...)
    ├── Level.java          modelo do nível
    ├── Ai.java             provedores de IA
    ├── Tools.java          base64, gzip, zlib
    └── Ui.java             tema e estilos
```

## Limitações

- O jogo não abre arquivos sozinho. Para levar o nível ao GD, use o mod GDShare/Geode (lê `.gmd` e `.lvl`) e faça o upload dentro do jogo. O `.gdlvl` tem o mesmo conteúdo do `.gmd`.
- O SPWN só é exportado como `.spwn` (propriedades com nome conhecido). O app não executa SPWN.
- Saves do GD no Windows 2.2 (AES) não são suportados. Saves XOR/gzip, como os do Android, são.
- O cabeçalho de um "Novo nível" é mínimo. Teste no jogo antes de subir.
