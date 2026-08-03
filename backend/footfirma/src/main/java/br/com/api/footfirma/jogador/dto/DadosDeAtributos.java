package br.com.api.footfirma.jogador.dto;

import java.time.OffsetDateTime;

/**
 * As 18 skills mais potencial e procedência, versionadas por temporada.
 * {@code coletadoEm} vem do dataset, não de {@code now()}: é quando o dado foi
 * coletado, não quando foi importado.
 */
public record DadosDeAtributos(Long jogadorId, Long temporadaId,
                               Integer ritmo, Integer forca, Integer folego, Integer salto,
                               Integer agilidade, Integer passe, Integer drible,
                               Integer cruzamento, Integer frieza, Integer finalizacao,
                               Integer cabeceio, Integer falta, Integer penalti,
                               Integer desarme, Integer marcacao, Integer golReflexo,
                               Integer golPosicionamento, Integer golManejo,
                               Integer potencialBase, Integer potencialVariacao,
                               String fonteAtributo, OffsetDateTime coletadoEm) {
}
