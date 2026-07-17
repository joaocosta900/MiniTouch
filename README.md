# MiniTouch — Pipeline de vídeo com detecção de mãos no Android

Arquitetura:

```
Câmera (CameraX) → Node Engine → [IA de mãos (MediaPipe) | GPU Shader OpenGL ES] → Renderizador final → Exportar vídeo (MediaCodec/MediaMuxer)
```

## Como compilar SEM Android Studio

Você tem duas opções. **Recomendo a Opção A** — é muito mais confiável, porque
compilar Android no ARM64/bionic do Termux exige remendar binários nativos
(aapt2, zipalign) que o Gradle baixa em versão x86_64 por padrão.

### Opção A — Build na nuvem via GitHub Actions (recomendado)

1. Crie uma conta gratuita em https://github.com (se ainda não tiver).
2. No Termux, instale o git e configure:
   ```
   pkg install git
   git config --global user.email "voce@exemplo.com"
   git config --global user.name "Seu Nome"
   ```
3. Extraia este zip, entre na pasta `MiniTouch`, e suba para um repositório novo:
   ```
   cd MiniTouch
   git init
   git add .
   git commit -m "MiniTouch inicial"
   git branch -M main
   git remote add origin https://github.com/SEU_USUARIO/MiniTouch.git
   git push -u origin main
   ```
   (Vai pedir login — use um Personal Access Token do GitHub como senha:
   Settings → Developer settings → Personal access tokens → Generate new token,
   marque o escopo `repo`.)
4. No GitHub, abra a aba **Actions** do repositório. O workflow
   `.github/workflows/build.yml` já está incluso e roda automaticamente a
   cada push (ou clique em "Run workflow" manualmente).
5. Quando o build terminar (ícone verde ✅), abra o run, desça até
   **Artifacts** e baixe `minitouch-debug-apk.zip` — dentro está o `.apk`.
6. Baixe esse `.apk` no celular (o link do GitHub abre direto no navegador do
   Android) e instale (com root/Termux: `pm install caminho/do/app.apk`, ou
   pelo gerenciador de arquivos tocando no `.apk`).

### Opção B — Build 100% local no Termux (offline após setup, mas instável)

Compilar Compose + CameraX + MediaPipe local no celular é pesado e o Gradle
baixa `aapt2` em binário x86_64, que **não roda** no ARM64 bionic do Termux —
é preciso substituir manualmente pelo aapt2 ARM64 do próprio Termux.

```bash
pkg update && pkg upgrade -y
pkg install openjdk-17 wget unzip git gradle aapt -y

# Baixe os cmdline-tools do Android SDK
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mv cmdline-tools latest

export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

cd ~/MiniTouch
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Primeira tentativa (vai falhar no aapt2 — é esperado, serve só pra baixar o jar em cache)
gradle assembleDebug

# Substitui o aapt2 x86_64 dentro do jar em cache pelo aapt2 ARM64 do Termux
find ~/.gradle -name 'aapt2-*-linux.jar' -type f | \
  xargs -I{} jar -u -f {} -C $PREFIX/bin aapt2

# Agora sim
gradle assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. Instale com:
```
pm install app/build/outputs/apk/debug/app-debug.apk
```

Se travar em outro binário nativo (ex: `d8`/`r8` raramente dá problema pois
são Java puro), a causa quase sempre é a mesma: ferramenta nativa x86_64
baixada pelo Gradle rodando em ARM64. Procure a versão ARM64 equivalente em
pacotes do Termux ou compile o projeto pela Opção A.

## Passo obrigatório em qualquer opção: modelo do MediaPipe

Sem isso o app compila mas crasha ao abrir a câmera:

```
mkdir -p app/src/main/assets
cd app/src/main/assets
wget https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
```
Faça isso **antes** do passo 3 da Opção A (pra ir junto no git push), ou
direto na pasta do projeto na Opção B.

## Arquitetura do código

| Camada | Pasta | Responsabilidade |
|---|---|---|
| Câmera | `camera/` | CameraX escrevendo direto numa textura OES via `SurfaceTexture` |
| Node Engine | `engine/node/` | `Node`, `NodeGraph`, `FrameData` — grafo genérico, ordenação topológica |
| Nós concretos | `engine/nodes/` | Câmera → Shader → Tracking de mãos → Overlay → Output |
| GL | `engine/gl/` | EGL manual, shaders, texturas, quad de desenho |
| IA de mãos | `hands/` | Wrapper do MediaPipe Tasks Hand Landmarker (LIVE_STREAM) |
| Exportação | `export/` | `MediaCodec` (Surface input) + `MediaMuxer` → `.mp4` |
| Orquestração | `pipeline/` | `PipelineManager`: 1 thread dedicada, 1 contexto EGL, loop de frames |
| UI | `ui/` | Compose: `SurfaceView` de preview + botão gravar |

## Grafo de nós atual

```
CameraInputNode ──► ShaderEffectNode ──► HandOverlayNode ──► OutputNode
  (textura OES)      (efeito GLSL,           ▲
                       OES→2D)                │
                          └──────► HandTrackingNode
                                  (MediaPipe, assíncrono, ~15 detecções/s)
```

## Limitações conhecidas

- `HandTrackingNode` usa `glReadPixels` (simples, não o mais rápido).
- Efeito de exemplo em `ShaderEffectNode` é dessaturação — troque o GLSL.
- Sem rotação de tela (fixado em portrait).
- Sem testes automatizados.
