package net.originmobi.pdv.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.originmobi.pdv.enumerado.caixa.EstiloLancamento;
import net.originmobi.pdv.enumerado.caixa.TipoLancamento;
import net.originmobi.pdv.model.Caixa;
import net.originmobi.pdv.model.CaixaLancamento;
import net.originmobi.pdv.repository.CaixaLancamentoRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaixaLancamentoServiceTest {

    @Mock
    private CaixaLancamentoRepository repo;

    @InjectMocks
    private CaixaLancamentoService service;

    private Caixa caixa;

    @Before
    public void prepararCaixa() { // caixa aberto com R$ 100 de saldo
        caixa = new Caixa();
        caixa.setValor_total(100.0);
    }

    // Testes do método lancamento

    // Legenda da organização dos testes: cada teste é numerado de C1 a C14, dizendo qual decisão do grafo é testada
    // C1 = caminho base, onde tudo dá certo (retorna na linha 85)
    // C2 = caixa ausente mas não fechada (linha 39)
    // C3 = caixa fechada mas não ausente (linha 40)
    // C4 = saída de caixa com saldo suficiente (linha 46)
    // C5 = saída de caixa com saldo insuficiente (linha 49)
    // C6 = saída na checagem do saldo mas entrada na hora de inverter o sinal (linha 55)
    // C7 = saída com valor menor ou igual a zero (linha 55)
    // C8 = observação preenchida (linha 62)
    // C9 = sangria sem observação (linha 64)
    // C10 = sangria com observação preenchida no ternário (linha 65)
    // C11 = nem sangria nem suprimento (linha 66)
    // C12 = suprimento com observação preenchida no ternário (linha 67)
    // C13 = erro dentro do primeiro try (linha 73)
    // C14 = erro ao salvar (linha 80)
    // C3, C6, C10 e C12 são caminhos inviáveis com um objeto real: o mesmo getter teria que mudar de resposta
    // entre duas leituras. Por isso usam mock com thenReturn(a, b), só para cobrir a aresta do grafo

    @Test
    public void testC1_SuprimentoSemObservacao() { // caminho base
        CaixaLancamento lanc = new CaixaLancamento("", 50.0, TipoLancamento.SUPRIMENTO, EstiloLancamento.ENTRADA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals("Suprimento de caixa", lanc.getObservacao());
        assertEquals(50.0, lanc.getValor(), 0.001);
        assertNotNull(lanc.getData_cadastro());
        verify(repo).save(lanc);
    }

    @Test
    public void testC1_EntradaEmCaixaSemSaldo() { // o saldo só é lido na saída, então entrada passa mesmo sem saldo
        caixa.setValor_total(null);
        CaixaLancamento lanc = new CaixaLancamento("", 50.0, TipoLancamento.SUPRIMENTO, EstiloLancamento.ENTRADA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        verify(repo).save(lanc);
    }

    @Test
    public void testC1_SuprimentoEmCaixaFechado() {
        caixa.setData_fechamento(new Timestamp(System.currentTimeMillis()));
        CaixaLancamento lanc = new CaixaLancamento("", 50.0, TipoLancamento.SUPRIMENTO, EstiloLancamento.ENTRADA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        verify(repo).save(lanc);
    }

    @Test
    public void testC2_SemCaixa() { // caixa nulo passa pelo if, porque "ausente E fechado" nunca é verdade junto
        CaixaLancamento lanc = new CaixaLancamento("", 50.0, TipoLancamento.SUPRIMENTO, EstiloLancamento.ENTRADA, null, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        verify(repo).save(lanc);
    }

    // caminho inviável com objeto real: o mock força a resposta só para cobrir a aresta
    @Test
    public void testC3_CaixaFechado() { // getCaixa() vem vazio na 1ª leitura e fechado na 2ª, entrando no if
        caixa.setData_fechamento(new Timestamp(System.currentTimeMillis()));
        CaixaLancamento lanc = mock(CaixaLancamento.class);
        when(lanc.getCaixa()).thenReturn(Optional.empty(), Optional.of(caixa));

        try {
            service.lancamento(lanc);
            fail("Era esperado recusar o caixa fechado");
        } catch (RuntimeException e) {
            assertNull(e.getMessage()); // "Nenhum caixa aberto" é trocado no catch por uma exceção sem mensagem
        }
        verify(lanc, never()).getEstilo(); // parou no throw da L41, antes do if da saída
        verify(repo, never()).save(any());
    }

    @Test
    public void testC4_SangriaIgualAoSaldo() { // saída igual ao saldo (limite) é aceita e vira negativa
        CaixaLancamento lanc = new CaixaLancamento("", 100.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals(-100.0, lanc.getValor(), 0.001);
        assertEquals("Sangria de caixa", lanc.getObservacao());
    }

    @Test
    public void testC5_SangriaAcimaDoSaldo() { // saída maior que o saldo para no if e não salva
        CaixaLancamento lanc = new CaixaLancamento("", 150.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Saldo insuficiente para realizar esta operação", obtido);
        verify(repo, never()).save(any());
    }

    @Test
    public void testC5_SangriaUmCentavoAcimaDoSaldo() { // 1 centavo a mais já é recusado
        CaixaLancamento lanc = new CaixaLancamento("", 100.01, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Saldo insuficiente para realizar esta operação", obtido);
        verify(repo, never()).save(any());
    }

    // caminho inviável com objeto real: o mock força a resposta só para cobrir a aresta
    @Test
    public void testC6_EstiloMuda() { // getEstilo() vem SAIDA no if do saldo e ENTRADA no if seguinte
        CaixaLancamento lanc = mock(CaixaLancamento.class);
        when(lanc.getCaixa()).thenReturn(Optional.of(caixa));
        when(lanc.getEstilo()).thenReturn(EstiloLancamento.SAIDA, EstiloLancamento.ENTRADA);
        when(lanc.getValor()).thenReturn(10.0);
        when(lanc.getObservacao()).thenReturn("Retirada");

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        verify(lanc, never()).setValor(any()); // não entrou no if que deixa o valor negativo
    }

    @Test
    public void testC7_SaidaNegativa() { // saída com valor negativo não entra no if que inverte o sinal e é salva como veio
        CaixaLancamento lanc = new CaixaLancamento("", -10.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals(-10.0, lanc.getValor(), 0.001);
    }

    @Test
    public void testC7_SangriaComValorZero() { // limite do "valor > 0", zero também não entra no if
        CaixaLancamento lanc = new CaixaLancamento("", 0.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals(Double.valueOf(0.0), lanc.getValor());
    }

    @Test
    public void testC7_SangriaPositivaViraNegativa() { // saída positiva entra no if e o sinal é invertido
        CaixaLancamento lanc = new CaixaLancamento("", 30.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals(-30.0, lanc.getValor(), 0.001);
        verify(repo).save(lanc);
    }

    @Test
    public void testC8_ComObservacao() { // observação preenchida pula o if e fica como veio
        CaixaLancamento lanc = new CaixaLancamento("Troco do dia", 50.0, TipoLancamento.SUPRIMENTO, EstiloLancamento.ENTRADA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals("Troco do dia", lanc.getObservacao());
    }

    @Test
    public void testC8_PagamentoComObservacao() { // saída com observação, vira negativa e mantém o texto
        CaixaLancamento lanc = new CaixaLancamento("Pagamento de despesas", 40.0, TipoLancamento.PAGAMENTO, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals(-40.0, lanc.getValor(), 0.001);
        assertEquals("Pagamento de despesas", lanc.getObservacao());
    }

    @Test
    public void testC8_SangriaComObservacao() { // como o C8 no suprimento: observação preenchida fica, e a saída vira negativa
        CaixaLancamento lanc = new CaixaLancamento("Depósito no banco", 20.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals(-20.0, lanc.getValor(), 0.001);
        assertEquals("Depósito no banco", lanc.getObservacao());
    }

    @Test
    public void testC9_SangriaSemObservacao() { // tipo sangria ganha o texto padrão de sangria
        CaixaLancamento lanc = new CaixaLancamento("", 30.0, TipoLancamento.SANGRIA, EstiloLancamento.ENTRADA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals("Sangria de caixa", lanc.getObservacao());
    }

    // caminho inviável com objeto real: o mock força a resposta só para cobrir a aresta
    @Test
    public void testC10_ObservacaoMudaNaSangria() { // getObservacao() vem vazia no if e preenchida no ternário
        CaixaLancamento lanc = mock(CaixaLancamento.class);
        when(lanc.getCaixa()).thenReturn(Optional.of(caixa));
        when(lanc.getEstilo()).thenReturn(EstiloLancamento.ENTRADA);
        when(lanc.getObservacao()).thenReturn("", "Retirada");
        when(lanc.getTipo()).thenReturn(TipoLancamento.SANGRIA);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        verify(lanc).setObservacao("Retirada"); // o ternário ficou com a observação, não com o texto padrão
    }

    @Test
    public void testC11_OutroTipoSemObservacao() { // tipo que não é sangria nem suprimento fica com observação vazia
        CaixaLancamento lanc = new CaixaLancamento("", 80.0, TipoLancamento.RECEBIMENTO, EstiloLancamento.ENTRADA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals("", lanc.getObservacao());
    }

    @Test
    public void testC12_SuprimentoSemObservacao() { // tipo suprimento ganha o texto padrão de suprimento, como o C9 na sangria
        CaixaLancamento lanc = new CaixaLancamento("", 30.0, TipoLancamento.SUPRIMENTO, EstiloLancamento.ENTRADA, caixa, null);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        assertEquals("Suprimento de caixa", lanc.getObservacao());
    }

    // caminho inviável com objeto real: o mock força a resposta só para cobrir a aresta
    @Test
    public void testC12_ObservacaoMudaNoSuprimento() { // mesmo caso do C10 mas para suprimento
        CaixaLancamento lanc = mock(CaixaLancamento.class);
        when(lanc.getCaixa()).thenReturn(Optional.of(caixa));
        when(lanc.getEstilo()).thenReturn(EstiloLancamento.ENTRADA);
        when(lanc.getObservacao()).thenReturn("", "Reforço");
        when(lanc.getTipo()).thenReturn(TipoLancamento.SUPRIMENTO);

        String obtido = service.lancamento(lanc);

        assertEquals("Lançamento realizado com sucesso", obtido);
        verify(lanc).setObservacao("Reforço");
    }

    @Test
    public void testC13_SangriaEmCaixaSemSaldo() { // saldo nulo dá erro dentro do try e cai no primeiro catch
        caixa.setValor_total(null);
        CaixaLancamento lanc = new CaixaLancamento("", 30.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, caixa, null);

        try {
            service.lancamento(lanc);
            fail("Era esperado erro ao ler o saldo do caixa");
        } catch (RuntimeException e) {
            assertNull(e.getMessage()); // o catch joga RuntimeException sem mensagem
        }
        verify(repo, never()).save(any());
    }

    @Test
    public void testC13_SangriaSemCaixa() { // sem caixa não tem saldo para ler, erro cai no primeiro catch
        CaixaLancamento lanc = new CaixaLancamento("", 30.0, TipoLancamento.SANGRIA, EstiloLancamento.SAIDA, null, null);

        try {
            service.lancamento(lanc);
            fail("Era esperado erro ao ler o saldo do caixa");
        } catch (RuntimeException e) {
            assertNull(e.getMessage());
        }
        verify(repo, never()).save(any());
    }

    @Test
    public void testC14_FalhaAoSalvar() { // erro no save cai no segundo catch
        CaixaLancamento lanc = new CaixaLancamento("", 50.0, TipoLancamento.SUPRIMENTO, EstiloLancamento.ENTRADA, caixa, null);
        when(repo.save(lanc)).thenThrow(new RuntimeException("banco fora do ar"));

        try {
            service.lancamento(lanc);
            fail("Era esperado erro ao salvar o lançamento");
        } catch (RuntimeException e) {
            assertEquals("Erro ao realizar lançamento, chame o suporte", e.getMessage());
        }
    }

    // Testes do método lancamentosDoCaixa

    @Test
    public void testLancamentosDoCaixa() { // caixa com lançamentos devolve a lista do repo
        List<CaixaLancamento> lancamentos = Arrays.asList(new CaixaLancamento());
        when(repo.findByCaixaEquals(caixa)).thenReturn(lancamentos);

        List<CaixaLancamento> obtido = service.lancamentosDoCaixa(caixa);

        assertSame(lancamentos, obtido);
    }

    @Test
    public void testLancamentosDoCaixaSemLancamentos() { // caixa sem nenhum lançamento devolve lista vazia
        when(repo.findByCaixaEquals(caixa)).thenReturn(Collections.<CaixaLancamento>emptyList());

        List<CaixaLancamento> obtido = service.lancamentosDoCaixa(caixa);

        assertEquals(0, obtido.size());
    }
}
