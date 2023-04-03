package tap.crawler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;  
import org.jsoup.nodes.Document;  

public class LeitorDePaginasWeb {
	
	private static ExecutorService threadPool = Executors.newFixedThreadPool(4);
	private static Set<String> LINKS_IDENTIFICADOS = new HashSet<>();
	private static final int LIMITADOR_DE_NIVEIS = 2;
	private static final int LIMITE_QTD_LINKS_POR_PAG = 3;
	private static final int SEGUNDOS_PARA_ENCERRAR_THREADS = 5; //Aumente a medida em que aumenta a qtd de niveis e links por pag
	private static int qtdLinks = 0;
	private static FileWriter fw;
	
	private static Map<String, Set<String>> indice = new HashMap<>(); //{"Campina", ["https://pt.wikipedia.org/wiki/Campina_Grande", "https://pt.wikipedia.org/wiki/Paraiba"]}
	private static Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(new String[]{"A", "O", "E", "AS", "OS", "UM", "UMA", "UNS", "UMAS"}));
	
	public static void lePagina(String url, int nivel) {
		
		try {
			URL u = new URL(url);
			
			//Ler e retorna conteudo html da pagina
			Scanner conteudoPagina = new Scanner(u.openStream());
			
			//Obtem conteudo de texto da pagina e indexa para buscas
			indexarPagina(url);
			
			extraiLinks(conteudoPagina, nivel);
			
			conteudoPagina.close();

		} catch (Exception ex) {
			System.err.println(ex);
		}
		return ;
	}

	public static String palavraToChave(String palavra) {
		return palavra.toLowerCase();
	}
	
	public static boolean isStopWord(String palavra) {
		if (palavra.length() <= 2) {
			return true;
		}
		
		return STOP_WORDS.contains(palavra);
	}
	
	public static void indexarPagina(String url) throws IOException {
		
		Document doc = Jsoup.connect(url).get(); //Para buscar o texto do pagina
		String conteudo = doc.body().text(); 
		
		String[] palavras = conteudo.split("\\s+");
		
		Arrays.stream(palavras)
		.map((p) -> palavraToChave(p))
		.filter((p) -> !isStopWord(p))
		.forEach((palavra) -> {		
			palavra = palavraToChave(palavra);
			
			Set<String> indicePorPalavra = null;
			
			if (indice.containsKey(palavra)) {
				indicePorPalavra = indice.get(palavra);
			} else {
				indicePorPalavra = new HashSet<>();
				indice.put(palavra, indicePorPalavra);
			}
			
			indicePorPalavra.add(url);
		});
	}
	
	private static void extraiLinks(Scanner conteudoPagina, int nivel) {
		
		String link_principal = "https://pt.wikipedia.org";
		
		if(nivel > LIMITADOR_DE_NIVEIS) {
			return;
		}
		
		try {
			fw.write("Nivel atual: "+nivel+"\n");
			int qtdLinksPorPag = 0;
			while (conteudoPagina.hasNextLine() && qtdLinksPorPag < LIMITE_QTD_LINKS_POR_PAG) {
				
				/* Procura por um path de link (Ex: href="/wiki/palavra")*/
				Pattern pattern = Pattern.compile("href\\s*=\\s*\"(\\/wiki\\/\\w+)\"");
				Matcher matcher = pattern.matcher(conteudoPagina.nextLine());
			
				if (matcher.find()) {
				    String link = matcher.group(1); //Busca somento o /wiki/palavra
				    
				    fw.write("Encontrado: "+link+"\n");
				    
				    /* Verificar se link já não foi visto */ 
				    if(LINKS_IDENTIFICADOS.contains(link)) {
				    	continue;
				    }		
				    LINKS_IDENTIFICADOS.add(link);
				    
				    String novoLink = link_principal.concat(link); //Concatena com o https://pt.wikipedia.org
				    				  				    
				    qtdLinks++;				    
				    System.out.println("Quantidade de links encontrados: "+qtdLinks);				    
				    System.out.println("Visitando o link: "+novoLink);
				    
				    fw.write("Visitando o link: ");
				    fw.write(novoLink);
				    fw.write("\n");				    
				    
				    qtdLinksPorPag++;
				    //Novo thread para pesquisar no proximo link
				    threadPool.submit(() -> {
				    	lePagina(novoLink, nivel+1);
				    });    
				}	
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}	
		
		return;
	}
	
	public static Set<String> buscar(String palavra) {
		String chave = palavraToChave(palavra);
		return indice.get(chave);
	}
	
	public static void main(String[] args) throws Exception {
		
		// Arquivo para verificar o fluxo do crawler
		fw = new FileWriter(new File("arq_gigante.txt"));
		
		lePagina("https://pt.wikipedia.org/wiki/Campina_Grande", 0);
        
		try {
			threadPool.awaitTermination(SEGUNDOS_PARA_ENCERRAR_THREADS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		}
		threadPool.shutdown();
		
		System.out.println("Links Indexados.");
        
		Scanner teclado = new Scanner(System.in);
		
		while (true) {
			System.out.print("Pesquisar (ou SAIR para sair): ");
			String busca = teclado.nextLine();
			if(busca.equals("SAIR")){ 
				break;
			}
			Set<String> resultado = buscar(busca);
			
			if (resultado == null || resultado.size() == 0) {
				System.out.println("Não foram encontrados resultados");
			} else {
				System.out.println(resultado.size() + " resultados: ");
				for (String arquivo : resultado) {
					System.out.println(arquivo);
				}
			}
		}
		
		teclado.close();
		fw.close();

	}
}
