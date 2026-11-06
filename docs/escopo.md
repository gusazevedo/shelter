# Abrace — escopo aprovado

Aplicativo Android interno, Kotlin/Jetpack Compose, Material You com cores dinâmicas e tema do sistema. Quatro destinos: Início, Animais, Adoções e Voluntários. Uso exclusivamente conectado, sem consulta offline. Dados compartilhados entre seis voluntários; início com cadastro vazio (sem importar Excel). Referência inicial: 23 animais e aproximadamente cinco adoções por mês.

## Animais e saúde

Nome, espécie (cão/gato), sexo, nascimento estimado (com referência temporal), raça, peso, cor, microchip opcional, data de entrada, até três fotos comprimidas, observações, base e disponibilidade. Duas bases iniciais: abrigo próprio e casa de apoio. Transferências preservadas. Internação é informação de saúde, não terceira moradia. Preservar histórico após adoção, devolução, recolhimento, arquivamento e falecimento.

Resumo de castração, vacinação antirrábica e leishmaniose distingue sim/não/desconhecido. Vacinas com data, próxima dose e observações. Tratamentos com início, fim por data ou número de aplicações, horários, repetição diária/semanal/por horas, aplicações realizadas e suspensão. Atraso não muda o calendário automaticamente. O app registra orientações recebidas; não prescreve tratamentos.

## Adoção e acompanhamento

A entrevista acontece fora do app; cadastrar apenas adoções efetivadas. Adotante: nome completo, nascimento/idade, CPF, telefone, e-mail, endereço completo e outros animais. O pet deve residir no endereço cadastrado. Documentos, comprovantes e termos ficam físicos; não armazenar anexos desses documentos, fotos de visitas ou exames nesta versão. Fotos dos animais são permitidas.

Cada animal mantém ciclos independentes de adoção. Não permitir duas adoções vigentes para o mesmo animal. Encerramento do acompanhamento não encerra a permanência no lar.

Três visitas presenciais por adoção; podem ser agendadas posteriormente. Uma visita pode acompanhar vários animais do mesmo adotante, com resultado e observações individuais. Resultados exatos: “bem-cuidado” e “precisa de atenção”. O visitante com edição decide, sem inferência automática baseada nos resultados, encerrar como “Adotado” ou “Recolhido”. Recolhimento pode acontecer antes da terceira visita e significa retorno físico à base de origem daquela adoção. Não permitir visitas adicionais. Retorno voluntário também deve preservar a adoção anterior. Animal retornado fica em avaliação até liberação para nova adoção.

## Voluntários e permissões

Lista de voluntários permanentes, sem gestão de escalas ou tarefas. Contas individuais por convite exclusivo do administrador, iniciado pelo botão de adicionar na listagem. Link recebido por e-mail permite criar senha; primeiro acesso sempre consulta. Consulta lê; edição cadastra e atualiza, sem excluir; administração gerencia tudo, inclusive acessos. Visitantes precisam de edição. Registros vinculados são arquivados; excluir definitivamente apenas cadastros sem vínculos feitos por engano. Voluntários podem ser desativados sem perder autoria histórica.

## Feed e infraestrutura

Feed geral de atrasos e próximos compromissos: vacinas, aplicações, visitas, acompanhamentos sem agendamento e encerramentos pendentes. Sem notificações push.

Meta financeira: zero mensalidade inicial, usando cotas gratuitas. Nenhuma contratação de domínio. Proposta: Supabase Free (Postgres, Auth, Storage, API e funções) e Gmail SMTP com conta dedicada, 2FA/senha de aplicativo quando disponível. Conta Play Console já existe. Dados e fotos precisam de backup externo; limites e pausa por inatividade do plano gratuito devem ser considerados na operação. Credenciais de servidor nunca entram no APK. Disponibilidade real e envio de e-mails só podem ser verificados após configuração das contas.
