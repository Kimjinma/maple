package com.example.maple.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/board")
    public String boardView() {
        // src/main/resources/templates/board.html 반환
        return "board";
    }
}
