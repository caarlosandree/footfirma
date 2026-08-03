package br.com.api.footfirma.importacao.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser de CSV suficiente para o formato do dataset: separador vírgula, aspas
 * duplas com escape por duplicação, campo vazio como nulo.
 *
 * <p>Não suporta quebra de linha dentro de aspas — o formato proíbe, e suportá-la
 * dobraria a complexidade do parser sem caso de uso.
 */
final class LeitorDeCsv {

    private static final char ASPAS = '"';
    private static final char SEPARADOR = ',';
    private static final char BOM = '﻿';

    private LeitorDeCsv() {
    }

    static List<LinhaDeCsv> ler(Path arquivo, List<String> cabecalhoEsperado) {
        var nome = arquivo.getFileName().toString();
        var brutas = lerTodasAsLinhas(arquivo, nome);
        if (brutas.isEmpty()) {
            throw new DatasetInvalidoException(nome + " está vazio: falta o cabeçalho");
        }

        var cabecalho = dividir(semBom(brutas.getFirst()));
        if (!cabecalho.equals(cabecalhoEsperado)) {
            throw new DatasetInvalidoException(
                    "%s: cabeçalho %s difere do esperado %s".formatted(nome, cabecalho, cabecalhoEsperado));
        }

        var linhas = new ArrayList<LinhaDeCsv>();
        for (var indice = 1; indice < brutas.size(); indice++) {
            var bruta = brutas.get(indice);
            if (bruta.isBlank()) {
                continue;
            }
            var numero = indice + 1;
            var campos = dividir(bruta);
            if (campos.size() != cabecalho.size()) {
                throw new DatasetInvalidoException(
                        "%s, linha %d: %d campos, esperados %d"
                                .formatted(nome, numero, campos.size(), cabecalho.size()));
            }
            linhas.add(new LinhaDeCsv(nome, numero, cabecalho, campos));
        }
        return linhas;
    }

    private static List<String> lerTodasAsLinhas(Path arquivo, String nome) {
        try {
            return Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível ler " + nome, erro);
        }
    }

    private static String semBom(String linha) {
        return linha.isEmpty() || linha.charAt(0) != BOM ? linha : linha.substring(1);
    }

    private static List<String> dividir(String linha) {
        var campos = new ArrayList<String>();
        var atual = new StringBuilder();
        var dentroDeAspas = false;

        for (var indice = 0; indice < linha.length(); indice++) {
            var caractere = linha.charAt(indice);
            if (dentroDeAspas) {
                if (caractere != ASPAS) {
                    atual.append(caractere);
                } else if (indice + 1 < linha.length() && linha.charAt(indice + 1) == ASPAS) {
                    atual.append(ASPAS);
                    indice++;
                } else {
                    dentroDeAspas = false;
                }
            } else if (caractere == ASPAS) {
                dentroDeAspas = true;
            } else if (caractere == SEPARADOR) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(caractere);
            }
        }
        campos.add(atual.toString());
        return campos;
    }
}
