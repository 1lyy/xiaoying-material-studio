package com.xiaoying.materialstudio.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException exception, HttpServletRequest request) {
        return error(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不受支持", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidRequest(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数无效", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "图片超过允许的大小", request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> invalidMultipart(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "上传请求无效", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedContentType(HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "请求的内容类型不受支持", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> known(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return error(status, "HTTP_" + status.value(), "请求无法完成", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Unexpected API error traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ApiError("INTERNAL_ERROR", "服务暂不可用，请稍后重试", traceId));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ApiError(code, message, traceId(request)));
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(ApiTraceFilter.TRACE_ID_ATTRIBUTE);
        return traceId instanceof String value ? value : UUID.randomUUID().toString();
    }
}
