package br.com.api.footfirma.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
class TratadorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excecao.getMessage());
        problema.setTitle("Recurso não encontrado");
        return problema;
    }

    @ExceptionHandler(DistribuicaoInvalidaException.class)
    ProblemDetail distribuicaoInvalida(DistribuicaoInvalidaException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
        problema.setTitle("Distribuição de skills inválida");
        return problema;
    }

    @ExceptionHandler(EscalacaoInvalidaException.class)
    ProblemDetail escalacaoInvalida(EscalacaoInvalidaException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
        problema.setTitle("Escalação inválida");
        return problema;
    }

    @ExceptionHandler(CalendarioInvalidoException.class)
    ProblemDetail calendarioInvalido(CalendarioInvalidoException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
        problema.setTitle("Calendário inválido");
        return problema;
    }

    @ExceptionHandler(ResultadoInvalidoException.class)
    ProblemDetail resultadoInvalido(ResultadoInvalidoException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
        problema.setTitle("Resultado inválido");
        return problema;
    }

    @ExceptionHandler(PropostaIndisponivelException.class)
    ProblemDetail propostaIndisponivel(PropostaIndisponivelException excecao) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, excecao.getMessage());
        problema.setTitle("Proposta indisponível");
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalido(MethodArgumentNotValidException excecao) {
        var problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problema.setTitle("Dados inválidos");
        problema.setProperty("campos", excecao.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        erro -> erro.getDefaultMessage() == null ? "inválido" : erro.getDefaultMessage(),
                        (primeiro, segundo) -> primeiro)));
        return problema;
    }
}
