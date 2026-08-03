package br.com.api.footfirma.mundo.internal;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Nome de clube é lista curada, não combinação de pools: são só 40, e um nome
 * sorteado tem chance real de cair num clube profissional existente. Nome de
 * jogador é combinatório — 1.520 nomes não se revisa à mão, e nenhum clube deste
 * mundo é real, então coincidência de nome não sugere identificação de pessoa.
 *
 * <p>As cidades são reais e resolvem contra o seed de geografia; os clubes que as
 * habitam, não.
 */
final class GeradorDeNomes {

    record NomeDeClube(String nomeCurto, String nomeOficial, String apelido,
                       String cidade, String uf) {
    }

    static final List<NomeDeClube> CLUBES = List.of(
            new NomeDeClube("Marena", "Sociedade Esportiva Marena", "Verdão da Serra", "São Paulo", "SP"),
            new NomeDeClube("Vulcano", "Vulcano Futebol Clube", "Fogo Azul", "Campinas", "SP"),
            new NomeDeClube("Serrazul", "Clube Atlético Serrazul", "Azulão", "Santos", "SP"),
            new NomeDeClube("Ipanorte", "Grêmio Ipanorte", "Tricolor do Norte", "Ribeirão Preto", "SP"),
            new NomeDeClube("Belarco", "Esporte Clube Belarco", "Arqueiro", "Rio de Janeiro", "RJ"),
            new NomeDeClube("Portomar", "Portomar Futebol Clube", "Marujo", "Niterói", "RJ"),
            new NomeDeClube("Alvorada", "Alvorada Atlético Clube", "Aurora", "Campos dos Goytacazes", "RJ"),
            new NomeDeClube("Riomanso", "Riomanso Futebol Clube", "Manso", "Belo Horizonte", "MG"),
            new NomeDeClube("Ventania", "Associação Atlética Ventania", "Vendaval", "Uberlândia", "MG"),
            new NomeDeClube("Aurinegro", "Clube Esportivo Aurinegro", "Ouro e Breu", "Juiz de Fora", "MG"),
            new NomeDeClube("Farol", "Farol Esporte Clube", "Luzeiro", "Porto Alegre", "RS"),
            new NomeDeClube("Pedra Alta", "Pedra Alta Futebol Clube", "Rochoso", "Caxias do Sul", "RS"),
            new NomeDeClube("Trevo", "Trevo Atlético Clube", "Sortudo", "Pelotas", "RS"),
            new NomeDeClube("Barra Fria", "Associação Desportiva Barra Fria", "Gelo", "Curitiba", "PR"),
            new NomeDeClube("Corvina", "Corvina Futebol Clube", "Peixe Prata", "Londrina", "PR"),
            new NomeDeClube("Lagoa Nova", "Lagoa Nova Esporte Clube", "Lagoense", "Maringá", "PR"),
            new NomeDeClube("Guaraciara", "Guaraciara Futebol Clube", "Guará", "Florianópolis", "SC"),
            new NomeDeClube("Salinas", "Salinas Atlético Clube", "Salineiro", "Joinville", "SC"),
            new NomeDeClube("Monte Claro", "Monte Claro Esporte Clube", "Montanhês", "Chapecó", "SC"),
            new NomeDeClube("Ferronorte", "Ferronorte Futebol Clube", "Trilho", "Salvador", "BA"),
            new NomeDeClube("Itapicuru", "Itapicuru Esporte Clube", "Ita", "Feira de Santana", "BA"),
            new NomeDeClube("Varjão", "Varjão Atlético Clube", "Varjonense", "Recife", "PE"),
            new NomeDeClube("Cabo Sul", "Cabo Sul Futebol Clube", "Cabista", "Caruaru", "PE"),
            new NomeDeClube("Rochedo", "Rochedo Esporte Clube", "Penhasco", "Fortaleza", "CE"),
            new NomeDeClube("Jatobá", "Jatobá Futebol Clube", "Jatobazeiro", "Juazeiro do Norte", "CE"),
            new NomeDeClube("Palmarina", "Sociedade Esportiva Palmarina", "Palma", "Natal", "RN"),
            new NomeDeClube("Vale Verde", "Vale Verde Atlético Clube", "Valeiro", "João Pessoa", "PB"),
            new NomeDeClube("Campo Belo", "Campo Belo Futebol Clube", "Belo", "Maceió", "AL"),
            new NomeDeClube("Icaraí", "Icaraí Atlético Clube", "Icaraiense", "Aracaju", "SE"),
            new NomeDeClube("Sertaneja", "Associação Atlética Sertaneja", "Sertão", "São Luís", "MA"),
            new NomeDeClube("Marimbondo", "Marimbondo Esporte Clube", "Ferrão", "Teresina", "PI"),
            new NomeDeClube("Nova Aurora", "Nova Aurora Futebol Clube", "Novato", "Belém", "PA"),
            new NomeDeClube("Cristalina", "Cristalina Esporte Clube", "Cristal", "Santarém", "PA"),
            new NomeDeClube("Piçarra", "Piçarra Atlético Clube", "Piçarrense", "Manaus", "AM"),
            new NomeDeClube("Bela Vista", "Bela Vista Futebol Clube", "Mirante", "Goiânia", "GO"),
            new NomeDeClube("Areial", "Areial Esporte Clube", "Areia", "Anápolis", "GO"),
            new NomeDeClube("Tamandiba", "Tamandiba Futebol Clube", "Tamandi", "Brasília", "DF"),
            new NomeDeClube("Ouro Fino", "Ouro Fino Atlético Clube", "Ourinho", "Cuiabá", "MT"),
            new NomeDeClube("Camboa", "Camboa Esporte Clube", "Camboense", "Campo Grande", "MS"),
            new NomeDeClube("Vila Rica", "Vila Rica Futebol Clube", "Vilarico", "Vitória", "ES"));

    static final List<String> PRENOMES = List.of(
            "Adriano", "Alan", "Anderson", "Bruno", "Caio", "Carlos", "Cauã", "Cléber",
            "Daniel", "Danilo", "Diego", "Edson", "Eduardo", "Emerson", "Fábio", "Felipe",
            "Fernando", "Gabriel", "Geraldo", "Gustavo", "Heitor", "Henrique", "Igor", "Ítalo",
            "Jonas", "Kaique", "Leandro", "Lucas", "Luiz", "Marcelo", "Mateus", "Murilo",
            "Nélson", "Otávio", "Paulo", "Rafael", "Renan", "Ricardo", "Rodrigo", "Samuel",
            "Sérgio", "Thiago", "Vinícius", "Wesley");

    static final List<String> SOBRENOMES = List.of(
            "Albuquerque", "Almeida", "Andrade", "Aragão", "Barbosa", "Bastos", "Bezerra",
            "Cardoso", "Carvalho", "Cavalcanti", "Correia", "Dantas", "Duarte", "Esteves",
            "Farias", "Fonseca", "Furtado", "Gonçalves", "Guimarães", "Leal", "Lacerda",
            "Macedo", "Machado", "Marinho", "Medeiros", "Meireles", "Nogueira", "Novaes",
            "Peixoto", "Pontes", "Quadros", "Rezende", "Sampaio", "Siqueira", "Tavares",
            "Teixeira", "Valente", "Vasconcelos", "Veloso", "Xavier");

    private GeradorDeNomes() {
    }

    /** Slug de API: legível, estável e derivado do nome. */
    static String slug(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
