# Entrega 2 — guia do grupo (COS482)

Guia interno da Entrega 2 (30% da nota, prazo 06/12/2026). As tarefas estão no milestone [Entrega 2](https://github.com/drbarros2004/pdv-QoS/milestone/2).

O ambiente continua o mesmo da Entrega 1: pré-requisitos, clone e comandos do dia a dia estão nas seções 1 a 3 de [ENTREGA1.md](ENTREGA1.md).

## 1. O que entregar

| Item | Issues | Ferramenta |
|---|---|---|
| Melhorar e ampliar os testes unitários, isolando dependências | #67 | JUnit 4 + Mockito |
| Testes de integração | #69 | JUnit 4 + MySQL do Docker |
| Medidas da ISO/IEC 25010 com GQM | #72 | Google Docs |
| Testes de sistema, pelo menos 1 E2E por integrante | #73 | Selenium (falta configurar) |
| Técnicas funcional, estrutural (≥ 80% de arestas) e baseada em defeitos (≥ 80% de mutação) | #74 | JaCoCo (ver a seção 4) e PIT (falta configurar) |
| Relatório de inspeção, com print antes e depois das correções | #75 | SonarQube, ver a seção 5 |
| Plano de Teste, README e slides | #71, #76, #77 | — |

## 2. Divisão das classes

Cada um continua com a classe da Entrega 1. As metas de cobertura de arestas e de escore de mutação valem para ela. Para o E2E, a sugestão é usar a funcionalidade dos testes manuais.

| Integrante | Classe | CC da classe / maior método (PMD) | Funcionalidade (manual e E2E) |
|---|---|---|---|
| Daniel Barros | `service/CaixaService.java` | 43 / 21 | Abertura e fechamento de caixa |
| Bernardo Pozzato | `service/RecebimentoService.java` | 41 / 24 | Cadastro de pessoas |
| Guilherme Hu | `service/CaixaLancamentoService.java` | 19 / 17 | Suprimento e retirada de caixa |
| Gabriel Schmitz | `service/notafiscal/NotaFiscalItemService.java` | 36 / 21 | Cadastro e edição de produtos |
| Pedro Cintra | `service/VendaService.java` | 48 / 19 | Fechamento de venda |

## 3. Convenções

- Os testes conferem o que a classe **deveria** fazer (regra do domínio), não o que ela faz hoje. Se o teste falha, achamos um defeito: abre issue e a correção entra junto com as do Sonar.
- **Tire o print do Sonar "antes" antes de mexer na sua classe**, inclusive para corrigir bug. Senão o print não mostra os problemas.
- Cada um cria a própria sub-issue de cada item, se atribui e coloca no milestone "Entrega 2".
- Branch, PR e issues de bug seguem a seção 7 de [ENTREGA1.md](ENTREGA1.md).
- O que não estiver linkado no `README.md` não é avaliado.

## 4. JaCoCo

O relatório é reproduzível a partir do commit. Para gerar o relatório do estado da Entrega 1 (útil para prints de "antes" nos slides), usar esses comandos com worktree vazia:

```sh
git switch --detach fc0c852 # último commit da Entrega 1
docker compose run --rm pdv-app mvn clean test "-Dmaven.clean.failOnError=false" "-Dmaven.test.failure.ignore=true"
docker compose run --rm --no-deps -v "${PWD}/target:/host-target" pdv-app cp -r target/site/jacoco /host-target/
start target/jacoco/index.html
git switch master
```

Para o relatório do estado atual, rode só o 2º e o 3º comando, sem trocar de commit. No relatório, a coluna **Branches** da sua classe é o critério todas-arestas (meta ≥ 80%).

## 5. SonarQube

O SonarQube roda em Docker separado do pdv, na porta 9000. A análise roda em outro container com Java 17, porque o SonarQube não aceita mais o Java 8 do `pdv-app`.

Comandos para subir e derrubar docker do sonarqube:

```sh
# sobe o SonarQube
docker compose -p sonarqube -f docker-compose.sonarqube.yml up -d

# derruba (os dados ficam salvos, -v apaga tudo)
docker compose -p sonarqube -f docker-compose.sonarqube.yml down
```

Abra http://localhost:9000 e entre com `admin` / `admin`. Na primeira vez ele vai pedir para trocar a senha.

Gerar o token (só uma vez):

1) Clique no seu avatar (canto superior direito) → My Account → Security
2) Em "Generate Tokens", dê um nome (tipo `pdv`), escolha o tipo **Global Analysis Token** e clique em Generate
3) Copie o token. Ele só aparece uma vez. **Não coloque o token em nenhum arquivo do repositório**

Rodar a análise:

```sh
# compila o projeto e manda a análise para o servidor
docker compose -p sonarqube -f docker-compose.sonarqube.yml run --rm -e SONAR_TOKEN=<seu token> scanner
```

Quando terminar, aparece `BUILD SUCCESS` e o projeto fica em http://localhost:9000/dashboard?id=net.originmobi%3Apdv.

Ver os problemas da sua classe:

1) No projeto, abra a aba **Issues**
2) No filtro da esquerda, em **File**, procure a sua classe
3) Tire o print da lista. Clicando em cada problema aparece a linha do código e a explicação da regra
4) Coloque esses prints no relatório de inspeção

Para o print do "depois": corrija a classe, rode a análise de novo e tire o print do mesmo filtro. Os prints e a explicação do que mudou vão no relatório de inspeção (Google Docs, link na #75).
