# Trabalho COS482 — Qualidade de Software (2026.2)

Fork de [repo-software-testing-courses/pdv](https://github.com/repo-software-testing-courses/pdv).

## Artefatos

| Artefato | Link |
|---|---|
| Setup do ambiente / guia da Entrega 1 | [docs/ENTREGA1.md](docs/ENTREGA1.md) |
| Guia da Entrega 2 | [docs/ENTREGA2.md](docs/ENTREGA2.md) |
| Código-fonte original (fork) | [src/](src/) |
| Testes unitários | [src/test/java](src/test/java) |
| Plano de Teste (IEEE 829) | [Google Docs](https://docs.google.com/document/d/1MENsC4y9bwNVXcliehmjZG5cn4AUcddNN_FSasJhsMM/edit?usp=sharing) |
| Casos de teste manuais (todos) | [Google Sheets](https://docs.google.com/spreadsheets/d/1Cty37931EZiXMWSM4s3-YRMZzpC-lXd195lgLeqfwbk/edit?usp=sharing) |
| Execuções no TestLink (1 cenário por funcionalidade, prints + PDF) | [testlink_evidences/](testlink_evidences/) |
| Bugs reportados | [Issues](https://github.com/drbarros2004/pdv-QoS/issues) |
| Complexidade ciclomática (PMD) | [metricas/pmd/pmd.txt](metricas/pmd/pmd.txt) |

## Integrantes

| Nome | GitHub | Teste unitário (classe) | Testes manuais (funcionalidade) | Cenário no TestLink |
|---|---|---|---|---|
| Daniel Barros | [@drbarros2004](https://github.com/drbarros2004) | [CaixaService](src/test/java/net/originmobi/pdv/service/CaixaServiceTest.java) | Abertura e fechamento de caixa | [daniel_barros/](testlink_evidences/daniel_barros/) |
| Bernardo Pozzato | [@bepo7](https://github.com/bepo7) | [RecebimentoService](src/test/java/net/originmobi/pdv/service/RecebimentoServiceTest.java) | Cadastro de pessoas | [Bernardo Pozzato/](testlink_evidences/Bernardo%20Pozzato/) |
| Guilherme Hu | [@guilherme-hu](https://github.com/guilherme-hu) | [CaixaLancamentoService](src/test/java/net/originmobi/pdv/service/CaixaLancamentoServiceTest.java) | Suprimento e retirada de caixa | [Guilherme Hu/](testlink_evidences/Guilherme%20Hu/) |
| Gabriel Schmitz | [@gabrielxmit10](https://github.com/gabrielxmit10) | [NotaFiscalItemService](src/test/java/net/originmobi/pdv/service/notafiscal/NotaFiscalItemServiceTest.java) | Cadastro e edição de produtos | [Gabriel Schmitz/](testlink_evidences/Gabriel%20Schmitz/) |
| Pedro Cintra | [@PedroCintra67](https://github.com/PedroCintra67) | [VendaService](src/test/java/net/originmobi/pdv/service/VendaServiceTest.java) | Fechamento de venda | [Pedro Cintra/](testlink_evidences/Pedro%20Cintra/) |

Cada integrante projetou e executou os casos de teste manuais da sua funcionalidade na
[planilha](https://docs.google.com/spreadsheets/d/1Cty37931EZiXMWSM4s3-YRMZzpC-lXd195lgLeqfwbk/edit?usp=sharing)
(uma aba por funcionalidade). Um desses casos foi também registrado e executado no TestLink.

## Como rodar

Precisa apenas do **Docker Desktop** — não instale Java, Maven nem MySQL.
Com o Docker Desktop aberto, na raiz do projeto:

```sh
docker compose up -d                          # sobe app + banco
docker compose logs -f pdv-app                # acompanha até "Started PdvApplication"
```

A aplicação sobe em <http://localhost:8080> — usuário `gerente`, senha `123`.
A primeira execução leva alguns minutos (baixa a imagem e as dependências Maven).

Testes: `docker compose run --rm pdv-app mvn test`

Todos os comandos e detalhes em [docs/ENTREGA1.md](docs/ENTREGA1.md) e [docs/ENTREGA2.md](docs/ENTREGA2.md).

---

## Sobre o sistema

Sistema de ERP web (PDV) desenvolvido em Java com Spring Framework.

### Recursos

- Cadastro de produtos / clientes / fornecedores
- Controle de estoque
- Gerenciar comandas
- Realizar venda
- Controle de fluxo de caixa
- Controle de pagar e receber
- Venda com cartões
- Gerenciar permissões de usuários por grupos
- Cadastrar novas formas de pagamento
- Relatórios

### Tecnologias

Java 8 · Spring Boot 2.0 · Spring Security · Thymeleaf 3 · Hibernate/JPA · Flyway · MySQL 8 · JasperReports
