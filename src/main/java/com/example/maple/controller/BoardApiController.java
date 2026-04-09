package com.example.maple.controller;

import com.example.maple.dto.board.BoardRequest;
import com.example.maple.dto.board.BoardResponse;
import com.example.maple.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardApiController {

    private final BoardService boardService;

    // 인증 없이 볼 수 있어야 함 (SecurityConfig에서 GET /api/board/** 에 대해 permitAll 처리 예정)
    @GetMapping
    public org.springframework.data.domain.Page<BoardResponse> findAll(
            @RequestParam(defaultValue = "0") int page) {
        return boardService.findAll(org.springframework.data.domain.PageRequest.of(
                page, 10, org.springframework.data.domain.Sort.by("id").descending()));
    }

    @GetMapping("/{id}")
    public BoardResponse findById(@PathVariable Long id) {
        return boardService.findById(id);
    }

    // 작성 (로그인 필수)
    @PostMapping
    public ResponseEntity<Long> save(
            @RequestBody BoardRequest request,
            @AuthenticationPrincipal UserDetails userDetails) { // Jwt 필터가 세팅해준 유저 정보
        Long boardId = boardService.save(request, userDetails.getUsername());
        return ResponseEntity.ok(boardId);
    }

    // 수정 (로그인 필수, 본인 또는 Admin만 가능)
    @PutMapping("/{id}")
    public ResponseEntity<Long> update(
            @PathVariable Long id,
            @RequestBody BoardRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long boardId = boardService.update(id, request, userDetails);
        return ResponseEntity.ok(boardId);
    }

    // 삭제 (로그인 필수, 본인 또는 Admin만 가능)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        boardService.delete(id, userDetails);
        return ResponseEntity.ok().build();
    }
}
