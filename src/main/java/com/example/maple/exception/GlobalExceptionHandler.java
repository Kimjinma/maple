package com.example.maple.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException e, Model model) {
        logger.warn("잘못된 요청 발생: {}", e.getMessage());
        model.addAttribute("errorMessage", "입력 값이 올바르지 않습니다: " + e.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception e, Model model) {
        logger.error("알 수 없는 오류 발생", e);
        model.addAttribute("errorMessage", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.<br>(" + e.getMessage() + ")");
        return "error";
    }
}
