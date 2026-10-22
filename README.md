# AutoTdah — Regulação produz +

Aplicativo Android nativo de autorregulação digital. Você monta uma sequência de até 10 etapas
(aplicativo ou regra + minutos), toca em **Iniciar** e o AutoTdah conduz a sequência sozinho,
bloqueando o que não estiver liberado em cada etapa.

```
Etapa 1  Chrome                30 min
Etapa 2  Todos os aplicativos  20 min
Etapa 3  Chrome                40 min
Etapa 4  Instagram             10 min
Etapa 5  Nenhum aplicativo     50 min
Etapa 6  Chrome                30 min   → total 3h
```

---

## 1. Situação desta entrega (honesta)

| Item | Situação |
|---|---|
| Código-fonte completo (lógica, serviços, 12 telas) | ✅ Escrito |
| Lógica central (máquina de estados, tempo, persistência, PIN, histórico, decisão de bloqueio) | ✅ **Compilada e testada: 43 testes passando** |
| Camada Android (serviços, acessibilidade, telas Compose) | ⚠️ **Escrita, ainda não compilada** — o ambiente onde foi produzida não tem acesso ao SDK do Android |
| APK | ⏳ Gerado automaticamente pelo GitHub (ver seção 3) ou pelo Android Studio |
| Teste em aparelho real | ⏳ Pendente (precisa de um celular Android) |

O bloqueio real **não foi validado em aparelho**. Testes unitários verdes não provam que o bloqueio funciona no seu celular.

---

## 2. Estrutura do projeto

```
app/src/main/java/br/com/autotdah/regulacaoproduzmais/
├── model/          Modelos: etapa, sessão, eventos, modelos de programação
├── domain/         Máquina de estados (SessionEngine), coordenação com persistência
│                   (SessionCoordinator), validação, decisão de bloqueio (AccessDecider)
├── data/           JSON mínimo, gravação atômica com cópia de segurança, stores
├── history/        Histórico em SQLite (HistoryDb) + interface testável
├── security/       PIN com PBKDF2 + salt, Keystore, limitação de tentativas
├── service/        Controlador central, serviço em primeiro plano, notificações, alarme
├── accessibility/  Serviço de acessibilidade e catálogo de apps
├── receiver/       Detecção de inicialização do aparelho
└── ui/             Telas em Jetpack Compose + tela de bloqueio
app/src/test/       Testes automatizados (JUnit 4)
.github/workflows/  Compilação automática do APK
```

Nada de lógica de tempo, armazenamento ou bloqueio fica nas telas.

---

## 3. Como gerar o APK

### Opção A — GitHub (sem instalar nada)
1. Crie um repositório no GitHub (pode ser privado) e envie **todo o conteúdo desta pasta**
   (no computador: arraste a pasta na página do repositório, ou use `git push`).
2. Abra a aba **Actions**. A compilação “Compilar APK” roda sozinha (≈ 5–8 min).
3. Ao terminar com sucesso, abra **Releases → “AutoTdah – última versão de teste”** e baixe
   `AutoTdah.apk` direto no celular. (Também fica em Actions → execução → Artifacts.)
4. Se a compilação falhar, copie o log de erro e envie para correção.

### Opção B — Android Studio
Abra a pasta do projeto → aguarde o Gradle sincronizar → **Build › Build App Bundle(s)/APK(s) › Build APK(s)**.
O arquivo sai em `app/build/outputs/apk/debug/app-debug.apk`.

Versões: Android Gradle Plugin 8.10.1 · Gradle 8.14.3 · Kotlin 2.0.21 · JDK 17 · compileSdk/targetSdk 35 · **minSdk 26 (Android 8.0)**.
A chave de assinatura de depuração está versionada (`app/debug.keystore`) para que cada novo APK
instale por cima do anterior sem perder dados. Ela **não** serve para publicar na Play Store.

---

## 4. Instalar e configurar no celular

1. Abra o `AutoTdah.apk` baixado. Se o Android pedir, permita “instalar apps desta fonte”.
2. Abra o AutoTdah → **Começar** → tela de configuração inicial.
3. **Acessibilidade (essencial):** toque em “Abrir acessibilidade” → *Apps instalados* (ou *Serviços baixados*) → **AutoTdah – bloqueio por etapas** → ativar.
   - **Android 13 ou mais novo:** apps instalados por APK aparecem com a opção bloqueada (“configuração restrita”).
     Vá em *Configurações › Apps › AutoTdah*, toque no menu **⋮** e escolha **Permitir configurações restritas**.
     Depois volte e ative a acessibilidade. (Há um botão para isso no próprio app.)
4. **Notificações:** permita, para ver a etapa e o tempo restante.
5. **Bateria:** marque o AutoTdah como **Sem restrição** (essencial em Xiaomi, Samsung, Motorola, Oppo, Realme).
6. **PIN:** crie um PIN de 4 a 8 dígitos. Ele é exigido para pausar ou encerrar.

---

## 5. Funcionalidades implementadas

- Programação de 1 a 10 etapas: adicionar, remover, reordenar, editar app e minutos, soma automática.
- Validação: sem números negativos, sem texto, mínimo 1 e máximo **480 min (8 h) por etapa**, etapa sem regra recusada, app desinstalado detectado.
- Seletor com todos os apps da gaveta (ícone + nome + busca) e as regras especiais “Todos” e “Nenhum”.
- Revisão antes de iniciar; início só por ação explícita.
- Contagem independente da interface; avanço automático entre etapas; aviso de nova etapa.
- Bloqueio por acessibilidade com tela própria (sem botão de “ignorar”); reaplicação imediata quando a etapa muda com um app já aberto.
- Pausa e encerramento distintos, ambos com PIN; limitação progressiva de tentativas (3 livres, depois 30 s, 1 min, 2 min… até 15 min), que sobrevive a reinícios.
- Reinício do aparelho → sessão **pausada** com tempo restante preservado → “Continuar programação” retoma do ponto salvo. Processamento idempotente (vários sinais de boot não duplicam nada).
- Recriação do processo pelo Android → sessão recuperada sem segunda execução.
- Histórico em SQLite: início/fim, planejado × executado, etapas concluídas/interrompidas, pausas, retomadas, reinícios, encerramento autenticado, falhas técnicas, “estado desconhecido”.
- Modelos de programação: salvar, abrir, usar, excluir (sem alterar sessões antigas).
- Configurações: PIN, apps monitorados, verificação de permissões e do serviço, modelos, limitações, limpar histórico e restaurar (bloqueados durante sessão ativa).
- Funciona offline, sem conta, sem servidor, sem publicidade; `allowBackup=false`.

### Escopo exato das regras
- **Aplicativo específico:** só ele fica liberado; os demais apps monitorados são bloqueados.
- **Todos os aplicativos:** período livre, nada é bloqueado pelo AutoTdah.
- **Nenhum aplicativo:** todos os apps monitorados ficam bloqueados.
- **Nunca bloqueados:** o próprio AutoTdah, tela inicial, Configurações, Telefone/discador e chamadas, emergência, teclado, interface do sistema, instalador e permissões.
- “Monitorados” = apps com ícone na gaveta (padrão) ou somente os escolhidos em Configurações.
- Pausada ou encerrada, a sessão não bloqueia nada.

---

## 6. Decisões técnicas

- **Tempo:** fonte da verdade = `consumido + (elapsedRealtime − âncora)`, nunca um contador que diminui a cada segundo. `elapsedRealtime` continua contando com a tela desligada e não muda se o usuário alterar o relógio.
- **Reinício:** cada inicialização é identificada por `Settings.Global.BOOT_COUNT`. Se mudou, a âncora é inválida: a sessão é pausada com o tempo salvo no último ponto de controle (a cada 15 s e no aviso de desligamento).
- **Persistência:** arquivo JSON gravado de forma atômica (`.tmp` + `fsync` + renomear) com cópia `.bak`. Arquivo ilegível nunca é apagado: é preservado como `.corrompido-*` e registrado no histórico.
- **Histórico consistente:** eventos são salvos junto com o estado (“caixa de saída”) e depois gravados no SQLite de forma idempotente — uma queda no meio não perde nem duplica eventos.
- **PIN:** PBKDF2-HMAC-SHA256, 120 000 iterações, salt de 16 bytes; registro cifrado com AES-256-GCM no Android Keystore. O PIN digitado nunca é gravado nem registrado.
- **Serviço:** foreground service tipo `specialUse` com notificação persistente. O laço de 1 s só avança etapas e atualiza a notificação; se o Android o pausar, o tempo continua correto.
- **Acessibilidade:** só o evento “janela mudou”, `canRetrieveWindowContent=false`, sem leitura de texto.
- **Sem bibliotecas desnecessárias:** Compose, AndroidX Core/Activity e Coroutines. SQLite nativo em vez de Room (sem geração de código).

---

## 7. Testes

Executados nesta entrega (lógica central, JVM): **43 passaram, 0 falharam**.

| # obrigatório | Teste |
|---|---|
| 1, 2, 3 | Programação de 1, 6 e 10 etapas |
| 4 | Soma correta dos minutos |
| 5, 6 | Transição automática e conclusão da última etapa |
| 7, 8 | Persistência ao fechar a interface e recuperação após recriação do processo |
| 9, 10, 11 | Pausa com PIN correto, recusa de PIN incorreto (com bloqueio progressivo), encerramento autenticado |
| 12 | Registro de eventos no histórico |
| 13, 14, 15 | Reinício pausa e preserva o tempo, retomada, sem sessões duplicadas com vários sinais de boot |
| 16 | App desinstalado (validação e decisão de bloqueio) |
| 17 | Acessibilidade desativada registrada uma única vez |
| 18 | Falhas de gravação (não inicia sem salvar; mantém progresso e grava ao recuperar) |
| 19 | Alteração do relógio civil não afeta a duração |
| 20 | “Tela desligada” por 95 min sem execução: avanço correto de várias etapas de uma vez |
| extras | Arquivo corrompido preservado, cópia de segurança, JSON, modelos, configurações, PIN em disco |

**Exigem aparelho real (não executados):** tela de bloqueio aparecendo sobre outro app, notificação, sobrevivência do serviço com tela desligada por horas, reinício físico do aparelho, comportamento por fabricante.
Roteiro sugerido: programação curta (Chrome 2 min → Nenhum 2 min → Todos 2 min), tentar abrir Instagram em cada etapa, desligar a tela, reiniciar o celular no meio, pausar com PIN errado 3 vezes.

---

## 8. Limitações reais do Android

O AutoTdah **não é inviolável**. Um app comum não tem controle absoluto sobre os demais. Dá para contornar:
- desativando o serviço de acessibilidade;
- forçando a parada ou desinstalando o app;
- alterando permissões, economia de bateria ou entrando no modo de segurança.

Além disso:
- Fabricantes podem matar serviços em segundo plano ou atrasar o aviso de inicialização. A contagem continua correta, mas o bloqueio pode demorar a reaparecer até o app ou a acessibilidade voltarem.
- Em desligamento abrupto (bateria arrancada, travamento), até ~15 s da etapa podem não ser contabilizados.
- No Android 12+ o sistema pode impedir que o serviço reinicie sozinho em segundo plano; isso é registrado no histórico e resolvido ao abrir o app.
- Se o PIN for esquecido durante uma sessão, a saída é desativar a acessibilidade do AutoTdah.
- Publicar na Play Store exigiria justificar o uso de acessibilidade e do serviço `specialUse` nas políticas do Google.

---

## 9. Próximas melhorias sugeridas

1. Validar em 2–3 aparelhos de fabricantes diferentes e ajustar o que aparecer.
2. Exportar histórico (CSV/PDF).
3. Estatísticas semanais (tempo de foco, etapas cumpridas).
4. Agendar início automático por dia da semana (ex.: horário da faculdade).
5. Modo “emergência” com atraso (ex.: pedir 10 min de espera antes de liberar).
6. Widget na tela inicial com a etapa atual.
7. Versão iOS via Screen Time API (exige autorização especial da Apple).
