# Issue tracker: GitHub

O acompanhamento deste projeto é feito nas issues de https://github.com/gusazevedo/shelter. Use a CLI `gh` com `--repo gusazevedo/shelter` para consultar e atualizar os tickets.

- Consultar: `gh issue view NUMERO --repo gusazevedo/shelter --comments`.
- Listar: `gh issue list --repo gusazevedo/shelter --state open`.
- Para textos com várias linhas, use um arquivo e `--body-file`.
- As dependências nativas do GitHub e a seção “Bloqueado por” registram a ordem de execução.
- Critérios de aceite só são concluídos com evidência; testes locais não substituem a validação no Android e no Supabase hospedado.

**PRs as a request surface: no.** Pull requests são entregas para revisão; as solicitações de trabalho ficam nas issues.
