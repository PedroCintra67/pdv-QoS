package net.originmobi.pdv.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import net.originmobi.pdv.controller.TituloService;
import net.originmobi.pdv.enumerado.EntradaSaida;
import net.originmobi.pdv.enumerado.VendaSituacao;
import net.originmobi.pdv.enumerado.caixa.EstiloLancamento;
import net.originmobi.pdv.enumerado.caixa.TipoLancamento;
import net.originmobi.pdv.model.Caixa;
import net.originmobi.pdv.model.CaixaLancamento;
import net.originmobi.pdv.model.PagamentoTipo;
import net.originmobi.pdv.model.Pessoa;
import net.originmobi.pdv.model.Receber;
import net.originmobi.pdv.model.Titulo;
import net.originmobi.pdv.model.Usuario;
import net.originmobi.pdv.model.Venda;
import net.originmobi.pdv.repository.VendaRepository;
import net.originmobi.pdv.service.cartao.CartaoLancamentoService;
import net.originmobi.pdv.singleton.Aplicacao;

@RunWith(MockitoJUnitRunner.class)
public class VendaServiceTest {

	private static final Long COD_VENDA = 1L;
	private static final Long COD_PAGAMENTO = 2L;
	private static final Long COD_TITULO = 3L;

	@Mock
	private VendaRepository vendas;

	@Mock
	private UsuarioService usuarios;

	@Mock
	private VendaProdutoService vendaProdutos;

	@Mock
	private PagamentoTipoService formaPagamentos;

	@Mock
	private CaixaService caixas;

	@Mock
	private ReceberService receberServ;

	@Mock
	private ParcelaService parcelas;

	@Mock
	private CaixaLancamentoService lancamentos;

	@Mock
	private TituloService tituloService;

	@Mock
	private CartaoLancamentoService cartaoLancamento;

	@Mock
	private ProdutoService produtos;

	@InjectMocks
	private VendaService service;

	private Venda venda;

	@Before
	public void setUp() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("operador", null));
		Field instanciaAplicacao = Aplicacao.class.getDeclaredField("aplicacao");
		instanciaAplicacao.setAccessible(true);
		instanciaAplicacao.set(null, null);
		venda = novaVenda(VendaSituacao.ABERTA, new Pessoa());
		when(vendas.findByCodigoEquals(COD_VENDA)).thenReturn(venda);
	}

	@Test
	public void fechaVendaFechadaLancaExcecao() {
		venda.setSituacao(VendaSituacao.FECHADA);

		assertLancaExcecao("venda fechada",
				() -> service.fechaVenda(COD_VENDA, COD_PAGAMENTO, 100.0, 0.0, 0.0,
						new String[] { "100" }, new String[] { COD_TITULO.toString() }));

		verifyZeroInteractions(formaPagamentos, receberServ, produtos);
	}

	@Test
	public void fechaVendaSemValorLancaExcecao() {
		assertLancaExcecao("Venda sem valor, verifique",
				() -> service.fechaVenda(COD_VENDA, COD_PAGAMENTO, 0.0, 0.0, 0.0,
						new String[] { "0" }, new String[] { COD_TITULO.toString() }));

		verifyZeroInteractions(formaPagamentos, receberServ, produtos);
	}

	@Test
	public void fechaVendaComFalhaAoCriarReceberLancaExcecao() {
		preparaPagamento("00", "CARTDEB");
		doThrow(new RuntimeException("banco indisponivel")).when(receberServ).cadastrar(any(Receber.class));

		assertLancaExcecao("Erro ao fechar a venda, chame o suporte",
				() -> fechaVenda(new String[] { "100" }));

		verify(vendas, never()).fechaVenda(anyLong(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any());
		verifyZeroInteractions(produtos);
	}

	@Test
	public void fechaVendaEmDinheiroSemCaixaAbertoLancaExcecao() {
		preparaPagamento("00", "DIN");
		when(caixas.caixaIsAberto()).thenReturn(false);

		assertLancaExcecao("nenhum caixa aberto", () -> fechaVenda(new String[] { "100" }));

		verify(lancamentos, never()).lancamento(any(CaixaLancamento.class));
		verify(vendas, never()).fechaVenda(anyLong(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any());
		verifyZeroInteractions(produtos);
	}

	@Test
	public void fechaVendaEmDinheiroSemValorDaParcelaLancaExcecao() {
		preparaPagamentoEmDinheiroComCaixaAberto();

		assertLancaExcecao("Parcela sem valor, verifique", () -> fechaVenda(new String[] { "" }));

		verify(lancamentos, never()).lancamento(any(CaixaLancamento.class));
		verifyZeroInteractions(produtos);
	}

	@Test
	public void fechaVendaEmDinheiroComParcelasDiferentesDoTotalLancaExcecao() {
		preparaPagamentoEmDinheiroComCaixaAberto();

		assertLancaExcecao("Valor das parcelas diferente do valor total de produtos, verifique",
				() -> fechaVenda(new String[] { "90" }));

		verify(lancamentos, never()).lancamento(any(CaixaLancamento.class));
		verifyZeroInteractions(produtos);
	}

	@Test
	public void fechaVendaEmDinheiroLancaNoCaixaFechaVendaEMovimentaEstoque() {
		Caixa caixa = preparaPagamentoEmDinheiroComCaixaAberto();
		Usuario usuario = new Usuario();
		when(usuarios.buscaUsuario("operador")).thenReturn(usuario);

		String mensagem = fechaVenda(new String[] { "100" });

		assertEquals("Venda finalizada com sucesso", mensagem);
		ArgumentCaptor<CaixaLancamento> captor = ArgumentCaptor.forClass(CaixaLancamento.class);
		verify(lancamentos).lancamento(captor.capture());
		CaixaLancamento lancamento = captor.getValue();
		assertEquals("Recebimento de venda á vista", lancamento.getObservacao());
		assertEquals(100.0, lancamento.getValor(), 0.0);
		assertEquals(TipoLancamento.RECEBIMENTO, lancamento.getTipo());
		assertEquals(EstiloLancamento.ENTRADA, lancamento.getEstilo());
		assertSame(caixa, lancamento.getCaixa().get());
		assertSame(usuario, lancamento.getUsuario());
		verificaFechamento(100.0, 0.0, 0.0);
		verify(produtos).movimentaEstoque(COD_VENDA, EntradaSaida.SAIDA);
	}

	@Test
	public void fechaVendaNoCartaoDeDebitoRegistraLancamento() {
		Titulo titulo = preparaPagamento("00", "CARTDEB");

		String mensagem = fechaVenda(new String[] { "100" });

		assertEquals("Venda finalizada com sucesso", mensagem);
		verify(cartaoLancamento).lancamento(100.0, Optional.of(titulo));
		verifyZeroInteractions(caixas, lancamentos);
		verificaFechamento(100.0, 0.0, 0.0);
		verify(produtos).movimentaEstoque(COD_VENDA, EntradaSaida.SAIDA);
	}

	@Test
	public void fechaVendaNoCartaoDeCreditoRegistraLancamento() {
		Titulo titulo = preparaPagamento("00", "CARTCRED");

		fechaVenda(new String[] { "100" });

		verify(cartaoLancamento).lancamento(100.0, Optional.of(titulo));
		verifyZeroInteractions(caixas, lancamentos);
	}

	@Test
	public void fechaVendaAPrazoSemClienteLancaExcecao() {
		venda.setPessoa(null);
		preparaPagamento("30", "DIN");

		assertLancaExcecao("Venda sem cliente, verifique", () -> fechaVenda(new String[] { "100" }));

		verify(parcelas, never()).gerarParcela(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
				any(Receber.class), anyInt(), anyInt(), any(Timestamp.class), any(Date.class));
		verifyZeroInteractions(produtos);
	}

	@Test
	public void fechaVendaAPrazoComParcelaVaziaLancaExcecao() {
		preparaPagamento("30", "DIN");

		assertLancaExcecao("valor de recebimento invalido", () -> fechaVenda(new String[] { "" }));

		verifyZeroInteractions(produtos);
	}

	@Test
	public void fechaVendaAPrazoGeraParcelaFechaVendaEMovimentaEstoque() {
		preparaPagamento("30", "DIN");

		String mensagem = fechaVenda(new String[] { "100" });

		assertEquals("Venda finalizada com sucesso", mensagem);
		ArgumentCaptor<Receber> receberCaptor = ArgumentCaptor.forClass(Receber.class);
		verify(receberServ).cadastrar(receberCaptor.capture());
		Receber receber = receberCaptor.getValue();
		assertEquals("Recebimento referente a venda 1", receber.getObservacao());
		assertEquals(100.0, receber.getValor_total(), 0.0);
		assertSame(venda.getPessoa(), receber.getPessoa());
		assertSame(venda, receber.getVenda());
		assertNotNull(receber.getData_cadastro());
		verify(parcelas).gerarParcela(eq(100.0), eq(0.0), eq(0.0), eq(0.0), eq(100.0), eq(receber), eq(0),
				eq(1), any(Timestamp.class), any(Date.class));
		verificaFechamento(100.0, 0.0, 0.0);
		verify(produtos).movimentaEstoque(COD_VENDA, EntradaSaida.SAIDA);
	}

	@Test
	public void fechaVendaAplicaDescontoEAcrescimoNoTotalFinal() {
		preparaPagamento("00", "CARTDEB");

		service.fechaVenda(COD_VENDA, COD_PAGAMENTO, 100.0, 10.0, 20.0,
				new String[] { "100" }, new String[] { COD_TITULO.toString() });

		verificaFechamento(110.0, 10.0, 20.0);
	}

	@Test
	public void fechaVendaComFalhaNoRepositorioLancaExcecaoENaoMovimentaEstoque() {
		preparaPagamento("00", "CARTDEB");
		doThrow(new RuntimeException("banco indisponivel")).when(vendas).fechaVenda(anyLong(), any(), anyDouble(),
				anyDouble(), anyDouble(), any(Timestamp.class), any(PagamentoTipo.class));

		assertLancaExcecao("Erro ao fechar a venda, chame o suporte",
				() -> fechaVenda(new String[] { "100" }));

		verifyZeroInteractions(produtos);
	}

	private Venda novaVenda(VendaSituacao situacao, Pessoa pessoa) {
		Venda novaVenda = new Venda();
		novaVenda.setCodigo(COD_VENDA);
		novaVenda.setSituacao(situacao);
		novaVenda.setPessoa(pessoa);
		return novaVenda;
	}

	private Titulo preparaPagamento(String forma, String siglaTitulo) {
		PagamentoTipo pagamento = new PagamentoTipo();
		pagamento.setCodigo(COD_PAGAMENTO);
		pagamento.setFormaPagamento(forma);
		when(formaPagamentos.busca(COD_PAGAMENTO)).thenReturn(pagamento);

		net.originmobi.pdv.model.TituloTipo tipo = new net.originmobi.pdv.model.TituloTipo();
		tipo.setSigla(siglaTitulo);
		Titulo titulo = new Titulo();
		titulo.setCodigo(COD_TITULO);
		titulo.setTipo(tipo);
		when(tituloService.busca(COD_TITULO)).thenReturn(Optional.of(titulo));
		return titulo;
	}

	private Caixa preparaPagamentoEmDinheiroComCaixaAberto() {
		preparaPagamento("00", "DIN");
		Caixa caixa = new Caixa();
		when(caixas.caixaIsAberto()).thenReturn(true);
		when(caixas.caixaAberto()).thenReturn(Optional.of(caixa));
		return caixa;
	}

	private String fechaVenda(String[] parcelasPagamento) {
		return service.fechaVenda(COD_VENDA, COD_PAGAMENTO, 100.0, 0.0, 0.0, parcelasPagamento,
				new String[] { COD_TITULO.toString() });
	}

	private void verificaFechamento(double total, double desconto, double acrescimo) {
		verify(vendas).fechaVenda(eq(COD_VENDA), eq(VendaSituacao.FECHADA), eq(total), eq(desconto), eq(acrescimo),
				any(Timestamp.class), any(PagamentoTipo.class));
	}

	private void assertLancaExcecao(String mensagemEsperada, Runnable acao) {
		try {
			acao.run();
			fail("Deveria ter lançado exceção: " + mensagemEsperada);
		} catch (RuntimeException e) {
			assertEquals(mensagemEsperada, e.getMessage());
		}
	}
}
