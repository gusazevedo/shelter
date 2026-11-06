# Abrace

App Android para a rotina interna da ONG Abrace: animais, adotantes, adoções responsáveis, acompanhamento presencial e saúde.

- Kotlin + Jetpack Compose + Material 3, cores dinâmicas e temas do sistema.
- Quatro áreas: Início, Animais, Adoções e Voluntários.
- Supabase (Postgres, Auth, Storage privado e API) com permissões verificadas no servidor.
- Convites por e-mail via SMTP configurável, incluindo Gmail, sem contratar domínio.
- Uso exclusivamente conectado. Dados de negócio não são mantidos offline; tokens são cifrados com uma chave do Android Keystore, e fotos não têm cache em disco.

O projeto começa sem dados de demonstração ou importação do Excel. Referência inicial: 23 animais, até três fotos por animal e seis voluntários.

## Executar

Requer JDK 17, Android SDK 36 e Build Tools 35.0.0. No Android Studio, abra a raiz do repositório e selecione JDK 17 para o Gradle. Pela linha de comando, configure `JAVA_HOME` para o JDK e `ANDROID_HOME` para o SDK (ou apenas `sdk.dir` em `local.properties`, que não é versionado).

Em um checkout novo:

```sh
git clone https://github.com/gusazevedo/shelter.git
cd shelter
sdkmanager 'platforms;android-36' 'build-tools;35.0.0'
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

O Gradle Wrapper baixa a versão 8.13 e verifica seu SHA-256. Plugins e dependências têm versões definidas no projeto; `app/gradle.lockfile` fixa as versões transitivas resolvidas. Ao atualizar dependências intencionalmente, execute as mesmas tarefas com `--write-locks` e versione a alteração do lockfile.

Sem URL/chave do Supabase, a compilação não exige conta, credencial ou serviço hospedado. Para conectar a uma instalação da ONG, configure `SUPABASE_URL` e `SUPABASE_ANON_KEY` em `local.properties` ou pelo ambiente. Use somente a chave pública anon/publishable no Android. Variáveis de ambiente têm prioridade sobre o arquivo local; valores vazios geram a versão que aguarda ativação.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Sem a configuração pública do projeto, o APK mostra que o Abrace aguarda ativação.

O passo a passo completo para criar o projeto gratuito, aplicar migrations, configurar Gmail, criar o primeiro administrador e publicar está em [docs/ativacao.md](docs/ativacao.md). O [escopo aprovado](docs/escopo.md) registra as decisões de produto.

## Verificar o backend

Com Docker em execução:

```sh
./scripts/test-backend.sh
```

O script cria um container PostgreSQL 17 exclusivo e descartável, com imagem fixada por digest e sem portas publicadas, e o remove ao terminar. Aplica as migrations e verifica permissões, isolamento de contas inativas, adoção única vigente, visitas individuais/compartilhadas, limite de três visitas, retorno com histórico, calendário de tratamentos, vacinação, arquivamento e restauração. O bootstrap reproduz as tabelas mínimas de Auth e Storage para testar SQL/RLS; não substitui testes reais de e-mail, Storage e autenticação hospedados.

## Verificações no GitHub e APK de teste

O workflow [Verificar Abrace](.github/workflows/verify.yml) roda em pushes, pull requests e execução manual. Executa as regras SQL, compila o APK, roda os testes Kotlin e o Android Lint, sem credenciais de produção. Falhas aparecem na aba **Actions** e nos checks do pull request; os relatórios de testes e lint ficam disponíveis como artefatos quando gerados.

Após todas as verificações passarem, a execução disponibiliza `abrace-debug-COMMIT_SHA` em **Actions > execução > Artifacts**, por 14 dias. O pacote contém o APK, `BUILD.txt` com a revisão e `SHA256SUMS` para conferência. É preciso entrar no GitHub para baixar os artefatos. Esse APK de debug aguarda ativação e não contém a URL/chave do projeto real. Para testar conectado, compile localmente com a configuração pública descrita acima.

Uma execução aprovada confirma compilação e testes automatizados. A validação da interface, câmera, links e permissões no Android e da integração completa no Supabase hospedado continua necessária nas issues correspondentes.

## Organização

- `app/src/main/java/br/org/abrace/ui`: telas, formulários e Material You.
- `app/src/main/java/br/org/abrace/data`: cliente HTTP, sessão cifrada e compressão de fotos.
- `app/src/main/java/br/org/abrace/domain`: registros imutáveis e regras de apresentação.
- `supabase/migrations`: esquema, permissões, API transacional e feed.
- `supabase/functions/invite-volunteer`: envio de convites autorizado pelo administrador.
- `scripts/backup.py`: exportação cifrada do banco e das fotos, para destino externo.

## Estado da entrega

Backend ativado no projeto da ONG: esquema, permissões, armazenamento privado de fotos e função de convites aplicados. Login administrativo, consulta dos dados, renovação de sessão e bloqueio de acesso anônimo foram verificados na API hospedada. O Android está configurado localmente com URL e chave pública, e o APK conectado foi compilado.

O remetente Gmail está configurado no Supabase com STARTTLS, e os modelos de convite e recuperação foram aplicados. A autenticação SMTP foi validada e o responsável confirmou o recebimento do e-mail de recuperação. O fluxo completo de convites, uploads e uso das telas ainda precisam de validação em Android. Assinatura privada, publicação pela Play Store e rotina operacional de backup também precisam ser configuradas antes de uso como sistema oficial.
