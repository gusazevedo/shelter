# Ativar o Abrace

O código funciona com um projeto Supabase real. O APK sem URL/chave pública mostra a tela de preparação, sem dados fictícios. O projeto da ONG já recebeu o banco, as permissões e a função de convites. O primeiro administrador foi criado e teve login e consulta validados na API. O Gmail e os modelos de e-mail estão configurados; a autenticação SMTP foi validada e o responsável confirmou o recebimento do e-mail de recuperação. A abertura dos links e os fluxos completos no Android ainda precisam ser validados.

## 1. Supabase Free

Crie um projeto gratuito e mantenha a senha do banco em um gerenciador de senhas. Não habilite faturamento/plano pago para este início. O app usa o endereço gratuito `https://PROJECT_REF.supabase.co`; nenhum domínio próprio é necessário.

Instale a CLI oficial do Supabase e execute na raiz:

```sh
supabase login
supabase link --project-ref PROJECT_REF
supabase db push
supabase functions deploy invite-volunteer
```

As migrations criam as tabelas, a API, as permissões e o bucket privado de fotos. O arquivo `supabase/config.toml` configura o ambiente **local**; os ajustes de autenticação abaixo também precisam ser aplicados no painel do projeto hospedado.

As duas bases iniciais são “Abrigo Abrace” e “Casa de apoio”. Seus nomes podem ser ajustados no painel antes do uso. Não há cadastros de animais, adoções ou voluntários de demonstração em produção.

## 2. Autenticação e Gmail sem domínio

No painel de autenticação:

- Desabilite cadastro público. Contas entram exclusivamente por convite.
- Use `abrace://auth` como Site URL e URL de redirecionamento permitida.
- Mantenha a confirmação de e-mail e configure mínimo de 10 caracteres para senha.
- Configure um SMTP personalizado com a conta Gmail escolhida: servidor `smtp.gmail.com`, porta 587 (STARTTLS) ou 465 (TLS), usuário/remetente iguais ao endereço Gmail completo.
- Ative a verificação em duas etapas da conta Google e gere uma senha de aplicativo, se essa opção estiver disponível para a conta. Use-a apenas na configuração SMTP do Supabase. Não use a senha pessoal, não coloque a credencial no APK e não a versione.

O SMTP padrão de testes do Supabase não atende aos convites de usuários reais. Gmail possui limites e pode bloquear envios considerados suspeitos; teste um convite e uma recuperação de senha antes de começar a operação. Não há promessa de disponibilidade ou entrega garantida no plano gratuito.

### Modelo do convite

No template **Invite user**, preserve `{{ .ConfirmationURL }}` como destino do botão. O Supabase confirma o convite e abre o app pelo endereço autorizado. Exemplo:

```html
<h2>Você foi convidado para o Abrace</h2>
<p>Instale o aplicativo Abrace no seu Android antes de aceitar o convite.</p>
<p><a href="https://play.google.com/store/apps/details?id=br.org.abrace">Instalar Abrace</a></p>
<p><a href="{{ .ConfirmationURL }}">Aceitar convite e criar senha</a></p>
<p>O link tem prazo de validade. Se ele expirar, solicite um novo convite ao administrador.</p>
```

Use `{{ .ConfirmationURL }}` também no template de recuperação de senha. Os links desta versão devem ser abertos **no Android com o app instalado**, não em um computador. Não existe portal público de adoção ou cadastro. Se a loja ainda não estiver publicada, instale o APK antes de testar o convite. Em aparelhos com outros apps que registrem o mesmo esquema, confirme a abertura no Abrace; domínio HTTPS próprio não faz parte deste escopo.

## 3. Primeiro administrador

No painel **Authentication > Users**, crie manualmente apenas a conta inicial do fundador com senha forte, sem expor a senha ao app. O gatilho cria o perfil inativo, sem privilégios automáticos. No SQL Editor, use o e-mail real dessa conta:

```sql
update public.volunteers
set full_name = 'SEU NOME', active = true, role = 'admin'
where email = lower('SEU_EMAIL');
```

Confirme que exatamente uma linha foi atualizada. Entre no Android com essa conta. Os demais voluntários são convidados pelo botão **Convidar** da listagem e começam com consulta. O app consulta a permissão no banco em cada operação; alterar metadados do usuário não concede privilégios. O último administrador ativo não pode ser removido ou rebaixado.

## 4. Configurar e compilar o Android

Use JDK 17, Android SDK 36 e Build Tools 35.0.0. O projeto inclui Gradle Wrapper. Nesta máquina, o JDK obtido para validação ficou em `$HOME/.cache/abrace/toolchain/jdk/Contents/Home`; ele pode ser selecionado com `export JAVA_HOME="$HOME/.cache/abrace/toolchain/jdk/Contents/Home"`. O SDK está indicado no `local.properties` local. Em `local.properties` (ignorado pelo Git), configure:

```properties
sdk.dir=/CAMINHO/DO/ANDROID/SDK
SUPABASE_URL=https://PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=CHAVE_PUBLICA_DO_PROJETO
```

A chave deve ser **anon/publishable**, nunca service-role/secret. A chave pública identifica o projeto; as regras RLS e funções verificam a identidade e a permissão reais de quem chama. A função de convites usa a chave de servidor fornecida automaticamente pelo ambiente de Edge Functions.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## 5. Publicar pela Play Store

O identificador inicial é `br.org.abrace`. Antes da primeira publicação, confirme se está disponível na conta existente e troque-o se necessário; atualize também o link do e-mail e a autoridade do FileProvider. Gere uma chave de upload privada e configure a assinatura de release no seu ambiente/Android Studio; ela não é versionada. Gere um AAB assinado, faça os testes na trilha interna e complete os formulários exigidos pelo Play Console, incluindo política de privacidade e informações de coleta de dados conforme a operação real da ONG.

Não publicar o APK de debug. A conta Play Console já existente foi informada pelo responsável. A publicação, os formulários e a assinatura ainda precisam ser realizados pelo titular/configurados com acesso às contas. O envio de e-mails já está configurado; o recebimento da recuperação foi confirmado, e os fluxos completos de convite e recuperação ainda precisam ser validados no Android.

## 6. Backup e limites

Referência inicial: 23 animais, no máximo três fotos cada, seis voluntários e cerca de cinco adoções por mês. Fotos são comprimidas para até 1 MiB; nessa referência, os arquivos ativos de fotos ficam abaixo de 69 MiB, sem contar substituições e histórico. O plano Free atualmente inclui 500 MB de banco, 1 GB de arquivos e franquias de transferência. Limites podem mudar; acompanhe o painel. Projetos de baixa atividade podem ser pausados após sete dias. O plano gratuito não inclui o backup automático gerenciado dos planos pagos.

Use `scripts/backup.py` para exportar banco e fotos para um local privado externo. Ele exige `age` e `pg_dump` (ou Docker) e produz um pacote cifrado. Forneça as credenciais por um arquivo privado/gerenciador de segredos no ambiente, nunca como argumentos de linha de comando ou no chat. Exemplo de execução após configurar o ambiente e gerar uma chave pública age:

```sh
python3 scripts/backup.py --recipient age1SUA_CHAVE_PUBLICA --output /LOCAL_PRIVADO/abrace.tar.gz.age
```

O script não foi executado contra um projeto real e a restauração ainda precisa ser testada em ambiente isolado. Não oferece consistência transacional entre banco e arquivos do Storage; evite alterações durante a exportação. O backup contém dados pessoais e deve ficar com acesso restrito; pode ser criptografado com `age`. Armazene a chave de restauração separadamente. Execute regularmente e após alterações importantes; agendamento e destino dependem de uma máquina/armazenamento sob responsabilidade da ONG. O registro dos responsáveis e a restauração de teste são necessários antes de depender deste sistema como único cadastro.

Banco e fotos são exportados separadamente dentro do mesmo pacote: um dump do banco sozinho não inclui os arquivos do Storage. Fotos substituídas não são apagadas pelo editor; o administrador pode revisar arquivos sem referência no painel do Storage para evitar consumo acumulado.

## Verificação de aceitação com contas reais

1. Entrar como administrador; cadastrar animal, adotante e tratamento.
2. Convidar outro endereço; abrir no Android, criar senha e verificar consulta sem botões de edição.
3. Conceder edição; registrar adoção, agendar visita e avaliar cada animal separadamente.
4. Completar três visitas e encerrar manualmente como Adotado; confirmar que uma nova adoção para o mesmo animal é impedida.
5. Em outro ciclo, recolher antes da terceira visita; verificar retorno à base de origem, avaliação e preservação do histórico.
6. Desligar a internet e confirmar que os dados deixam de ser exibidos.
7. Desativar um usuário e confirmar a revogação do acesso.
8. Executar backup e testar restauração em projeto de teste separado.

## Referências oficiais verificadas

- https://supabase.com/pricing
- https://supabase.com/docs/guides/platform/backups
- https://supabase.com/docs/guides/auth/auth-smtp
- https://supabase.com/docs/guides/auth/users
- https://support.google.com/accounts/answer/185833?hl=pt-BR
- https://developers.google.com/workspace/gmail/imap/imap-smtp
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
