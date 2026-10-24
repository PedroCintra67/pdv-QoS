# Setup do ambiente — COS482 (grupo)

Sistema escolhido: **pdv** (ERP web em Java 8 / Spring Boot 2.0 + Thymeleaf 3 + MySQL 8 + Flyway), fork de
`repo-software-testing-courses/pdv`.

Tudo roda em Docker. **Não é preciso instalar Java, Maven nem MySQL** na máquina.

## 1. Pré-requisitos

| Ferramenta | Por quê | Onde |
|---|---|---|
| Docker Desktop | roda app, banco, Maven e testes | https://docs.docker.com/desktop/ |
| JDK 8 local | **só** se quiser autocomplete/debug na IDE. Os testes não precisam. | Temurin 8 |

> O Docker Desktop precisa estar **aberto** antes de qualquer comando `docker`.

## 2. Clonar

```sh
git clone https://github.com/drbarros2004/pdv-QoS.git
cd pdv-QoS
```

## 3. Comandos do dia a dia

Não há script de atalho: são comandos `docker compose` diretos, para o grupo entender
o que roda por trás. Todos a partir da raiz do projeto, com o Docker Desktop aberto.

```sh
# sobe aplicação + banco (http://localhost:8080)
docker compose up -d

# acompanha o log até aparecer a linha "Started PdvApplication"
docker compose logs -f pdv-app

# roda os testes (sobe o banco sozinho se precisar)
docker compose run --rm pdv-app mvn test

# bash dentro do container, com mvn e java disponíveis
docker compose run --rm pdv-app bash

# derruba tudo
docker compose down

# apaga o banco e recria do zero (as migrações do Flyway rodam de novo)
docker compose down -v && docker compose up -d
```

Login do sistema: usuário `gerente`, senha `123`.

O `docker compose up` roda `mvn package -DskipTests` antes de iniciar a aplicação, o que
na primeira vez baixa todas as dependências Maven: medimos ~6 min do zero absoluto
(sem imagem e sem cache) até a aplicação responder. Depois fica rápido — o `~/.m2`
fica guardado no volume `m2`.

## 4. Onde escrever os testes

```
src/test/java/net/originmobi/pdv/service/VendaServiceTest.java   # espelha o pacote da classe testada
```

JUnit 4 e Mockito **já vêm** no `spring-boot-starter-test`; não precisa mexer no `pom.xml`.

Testes unitários devem ser puros (Mockito, sem `@SpringBootTest`), para não depender do banco.

## 5. Divisão das classes (1 classe de alta complexidade por integrante)

O requisito é complexidade ciclomática ≥ 10 por classe. Contabilizamos com o **PMD 7.17.0**, regra
[`CyclomaticComplexity`](https://pmd.github.io/pmd/pmd_rules_java_design.html#cyclomaticcomplexity):
cada método começa em 1 e soma +1 por `if`, `case`, laço, `catch`, `throw`, `?:`, `&&` e `||`.
A CC da classe é a soma dos métodos. Medido em 11/10/2026:

| Classe | CC da classe | Método mais complexo | Responsável |
|---|---|---|---|
| `service/VendaService.java` | 48 | 19 | |
| `service/CaixaService.java` | 43 | 21 | |
| `service/RecebimentoService.java` | 41 | 24 | |
| `service/notafiscal/NotaFiscalItemService.java` | 36 | 21 | |
| `service/notafiscal/NotaFiscalService.java` | 28 | 11 | |
| `service/cartao/CartaoLancamentoService.java` | 27 | 9 | |
| `service/CaixaLancamentoService.java` | 19 | 17 | |
| `service/PagarService.java` | 18 | 11 | |

As entidades de `model/` também passam de 30, mas só porque somam dezenas de getters e setters de CC 1. Essas classes são CRUD, e não entram no escopo do trabalho.

OBS: além dos casos citados acima, o PMD adiciona +1 na CC a cada `throw`, então os números do PMD saem um pouco maiores que a contagem na mão

Para medir de novo, utilize o comando abaixo:

```sh
# gera metricas/pmd/pmd.txt com a CC de todas as classes e métodos
docker compose run --rm --no-deps pdv-app mvn pmd:pmd
```

## 6. Responsabilidades do grupo

| Integrante | GitHub | Classe unitária | Funcionalidade (teste manual) | Outros artefatos |
|---|---|---|---|---|
| | | | | |

## 7. Convenções

- Branch por tarefa: `test/venda-service`, `docs/plano-de-teste`. Nada direto na `master`.
- PR para `master` com pelo menos 1 revisão de outro integrante.
- **Todos os artefatos entregues precisam estar na `master`** e linkados no `README.md`.
- Bugs encontrados viram **GitHub Issues** neste repositório (é o bug tracker da Entrega 1).
- Documentos de texto no **Google Docs**, cada um logado na própria conta. Só os links vão no README.

## 8. Ferramental por entrega

Disponível sem fazer nada: JUnit 4 e Mockito (vêm do `spring-boot-starter-test`) e o ambiente Docker.

| Entrega | Ferramenta | Quando adicionar |
|---|---|---|
| 1 | TestLink (casos manuais) | já disponível, ver [TestLink](#testlink) |
| 1 | PMD (complexidade ciclomática) | já disponível, ver a seção 5 |
| 1 | GitHub Issues | já disponível |
| 2 | JaCoCo (cobertura) | já disponível, ver [JaCoCo](#jacoco) |
| 2 | PIT (`pitest-maven`) — escore de mutação | adicionar ao `pom.xml` na Entrega 2 |
| 2 | SonarQube (`sonar-scanner` ou SonarCloud) | Entrega 2 |
| 2 | Selenium WebDriver — testes E2E | Entrega 2 |

### TestLink

O TestLink roda em Docker separado do pdv, na porta 8090.

```sh
# sobe o TestLink
docker compose -p testlink -f docker-compose.testlink.yml up -d

# derruba
docker compose -p testlink -f docker-compose.testlink.yml down
```

Abra o link http://localhost:8090. 
Na primeira vez vai abrir o instalador. Clicar em "New installation", aceitar a licença e preencher o banco assim:

| Campo | Valor |
|---|---|
| Database type | MySQL/MariaDB |
| Database host | `db` |
| Database name | `testlink` |
| Table prefix | vazio |
| Database admin login / senha | `root` / `teste` |
| TestLink DB login / senha | `teste` / `teste` |

Depois que a instalação concluir, reabra http://localhost:8090 e entre com `admin` / `admin`. Vai abrir uma tela de criar um projeto novo, pode nomear o projeto como "pdv", prefixo "PDV" e resto das configurações default. Depois, só seguir o passo a passo abaixo:

1) Aperte na aba "Test Specification". Dentro dessa página, na parte da direita, aperte na Engrenagem e depois no símbolo de "+". Aqui você vai criar a suíte (conjunto de testes) da sua funcionalidade, dê um nome e os detalhes
2) Clique na nova pasta criada, e aperte na Engrenagem de novo, dessa vez selecionando o símbolo de "+" para Test Case. Na nova tela, preencha os detalhes sobre o cenário a ser testado. OBS: o nome do teste é "ID (da planilha) - Nome do caso"
3) Com o Test Case criado, aperte em "Create step" e escreva o passo a passo
4) Volte ao home e clique em "Test Plan Management" (na direita da tela). Crie o plano e marque como Active e Public
5) Volte ao Home e vá em Builds / Releases (direita da tela): crie um build com o commit a ser testado, passando o id do commit e branch, e novamente configure como Active e Open
6) Volte ao home e aperte em "Add / Remove Test Cases". Na árvore de pastas na parte inferior da tela, aperte na suíte criada, vai aparecer a lista de testes criados. Selecione na caixinha branca, em seguida aperte em "Add selected", a linha deve ficar amarela
7) Execução do teste: suba o docker do pdv de novo, ao mesmo tempo volte ao home do TestLink, onde você deve apertar "Execute Tests". Verifique se o build está correto, em seguida abra a árvore e selecione o teste. Com isso, vai aparecer exatamente o Caso de Teste a ser executado e basta preenchê-lo, de forma escrita e com prints

### JaCoCo

O relatório é reproduzível a partir do commit. Para gerar o relatório do estado
da Entrega 1 (útil para prints de "antes" nos slides), usar esses comandos com worktree vazia:

```sh
git switch --detach fc0c852 # último commit da Entrega 1
docker compose run --rm pdv-app mvn clean test "-Dmaven.clean.failOnError=false" "-Dmaven.test.failure.ignore=true"
docker compose run --rm --no-deps -v "${PWD}/target:/host-target" pdv-app cp -r target/site/jacoco /host-target/
start target/jacoco/index.html
git switch master
```

## 9. Prazos

| Marco | Data |
|---|---|
| Formação do grupo e escolha do sistema | 03/09/2026 |
| Repositório do grupo + fork | 10/09/2026 |
| **Entrega 1** (20%) | 11/10/2026, 23h59 |
| Apresentação da Entrega 1 | 13/10 e 15/10 |
| **Entrega 2** (30%) | 06/12/2026, 23h59 |
| Apresentação final | 08/12, 10/12 e 15/12 |
