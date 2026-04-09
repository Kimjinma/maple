package com.example.maple.service;

import com.example.maple.domain.Board;
import com.example.maple.domain.User;
import com.example.maple.dto.board.BoardRequest;
import com.example.maple.dto.board.BoardResponse;
import com.example.maple.repository.BoardRepository;
import com.example.maple.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BoardResponse> findAll(org.springframework.data.domain.Pageable pageable) {
        return boardRepository.findAll(pageable).map(BoardResponse::new);
    }

    @Transactional(readOnly = true)
    public BoardResponse findById(Long id) {
        Board board = boardRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        return new BoardResponse(board);
    }

    @Transactional
    public Long save(BoardRequest request, String userId) {
        User user = userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 유저입니다."));

        Board board = Board.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .user(user)
                .build();

        return boardRepository.save(board).getId();
    }

    @Transactional
    public Long update(Long id, BoardRequest request, UserDetails userDetails) {
        Board board = boardRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        // 유저/어드민 권한 체크 (ROLE_ADMIN은 무조건 통과, 아니면 글의 userId와 현재 userId 비교)
        checkPermission(board, userDetails);

        // 업데이트 수행
        board.update(request.getTitle(), request.getContent());
        return id;
    }

    @Transactional
    public void delete(Long id, UserDetails userDetails) {
        Board board = boardRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        // 유저/어드민 권한 체크
        checkPermission(board, userDetails);

        boardRepository.delete(board);
    }

    private void checkPermission(Board board, UserDetails userDetails) {
        String currentUserId = userDetails.getUsername(); // 우리 로직상 DB PK
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !board.getUser().getId().toString().equals(currentUserId)) {
            throw new IllegalArgumentException("본인이 작성한 글만 수정/삭제 권한이 있습니다.");
        }
    }
}
