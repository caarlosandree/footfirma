package br.com.api.footfirma.mundo.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/** As 18 skills, na ordem em que o schema as declara. */
record AtributosGerados(int ritmo, int forca, int folego, int salto, int agilidade,
                        int passe, int drible, int cruzamento, int frieza, int finalizacao,
                        int cabeceio, int falta, int penalti, int desarme, int marcacao,
                        int golReflexo, int golPosicionamento, int golManejo) {

    /** Usado pelos testes para varrer as 18 sem repetir o nome de cada uma. */
    Map<String, Integer> todas() {
        var mapa = new LinkedHashMap<String, Integer>();
        mapa.put("RITMO", ritmo);
        mapa.put("FORCA", forca);
        mapa.put("FOLEGO", folego);
        mapa.put("SALTO", salto);
        mapa.put("AGILIDADE", agilidade);
        mapa.put("PASSE", passe);
        mapa.put("DRIBLE", drible);
        mapa.put("CRUZAMENTO", cruzamento);
        mapa.put("FRIEZA", frieza);
        mapa.put("FINALIZACAO", finalizacao);
        mapa.put("CABECEIO", cabeceio);
        mapa.put("FALTA", falta);
        mapa.put("PENALTI", penalti);
        mapa.put("DESARME", desarme);
        mapa.put("MARCACAO", marcacao);
        mapa.put("GOL_REFLEXO", golReflexo);
        mapa.put("GOL_POSICIONAMENTO", golPosicionamento);
        mapa.put("GOL_MANEJO", golManejo);
        return mapa;
    }
}
