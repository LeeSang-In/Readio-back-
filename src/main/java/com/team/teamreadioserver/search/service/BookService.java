package com.team.teamreadioserver.search.service;

import com.team.teamreadioserver.search.client.ExternalBookApiClient;
import com.team.teamreadioserver.search.dto.BookDTO;
import com.team.teamreadioserver.search.dto.BooksDTO;
import com.team.teamreadioserver.search.entity.Book;
import com.team.teamreadioserver.search.repository.BookRepository;
import lombok.RequiredArgsConstructor;
// import org.modelmapper.ModelMapper; // 현재 코드에서 ModelMapper는 사용되지 않고 있어서 주석 처리해도 될 것 같습니다.
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger; // 로깅을 위해 추가
import org.slf4j.LoggerFactory; // 로깅을 위해 추가

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookService {

    private static final Logger logger = LoggerFactory.getLogger(BookService.class); // 로거 추가
    private final BookRepository bookRepository;
//    private final ExternalBookApiClient externalClient;

    @Transactional
    public BooksDTO searchBooks(String keyword, int page, int size) {
        //  DB 내 제목/저자에 해당 키워드 포함된 도서 조회
        List<Book> byTitle  = bookRepository.findAllByBookTitleContaining(keyword);
        List<Book> byAuthor = bookRepository.findAllByBookAuthorContaining(keyword);

        LinkedHashSet<Book> combined = new LinkedHashSet<>();
        combined.addAll(byTitle);
        combined.addAll(byAuthor);

        logger.debug("DB 검색 결과 (제목/저자 '{}'): {} 건", keyword, combined.size());

        //  페이징 처리만 수행해서 반환 (외부 API 호출 없이 결과가 얼마든, 그대로 반환)
        return toPagedDto(new ArrayList<>(combined), page, size);
    }

    @Transactional
    public BookDTO saveBook(BookDTO bookDto) {
        if (bookDto == null || bookDto.getBookIsbn() == null || bookDto.getBookIsbn().isBlank()) {
            throw new IllegalArgumentException("유효하지 않은 도서 정보입니다.");
        }

        Book existing = bookRepository.findByBookIsbn(bookDto.getBookIsbn());

        // 출판일 처리: DTO에 있으면 파싱, 없으면 기존 엔티티의 값을 사용하거나 null
        LocalDate pubDate = parsePubDate(bookDto.getBookPubdate());
        if (pubDate == null && existing != null) {
            pubDate = existing.getBookPubdate();
        }

        Book toSave = Book.builder()
                .bookIsbn(bookDto.getBookIsbn())
                .bookTitle(bookDto.getBookTitle())
                .bookAuthor(bookDto.getBookAuthor())
                .bookPublisher(bookDto.getBookPublisher())
                .bookCover(bookDto.getBookCover())
                .bookDescription(bookDto.getBookDescription())
                .bookPubdate(pubDate)
                .build();

        Book saved = bookRepository.save(toSave);
        return new BookDTO(
                saved.getBookIsbn(),
                saved.getBookTitle(),
                saved.getBookAuthor(),
                saved.getBookPublisher(),
                saved.getBookCover(),
                saved.getBookDescription(),
                saved.getBookPubdate() != null
                        ? saved.getBookPubdate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        : ""
        );
    }

    private LocalDate parsePubDate(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.isBlank()) {
            return null;
        }
        try {
            if (pubDateStr.matches("\\d{8}")) {
                return LocalDate.parse(pubDateStr, DateTimeFormatter.BASIC_ISO_DATE);
            } else if (pubDateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(pubDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } else {
                logger.warn("알 수 없는 날짜 형식: {}", pubDateStr);
                return null;
            }
        } catch (Exception e) {
            logger.warn("날짜 파싱 실패: {}, 입력값: {}", e.getMessage(), pubDateStr);
            return null;
        }
    }


    // -----------------------------------------------------------

    private BooksDTO toPagedDto(List<Book> list, int page, int size) {
        if (list == null) {
            return new BooksDTO(Collections.emptyList(), 0);
        }
        int total = list.size();
        List<BookDTO> dtos = list.stream()
                .skip((long)(page - 1) * size)
                .limit(size)
                .map(this::toDto) // Book 엔티티를 BookDTO로 변환
                .collect(Collectors.toList());
        return new BooksDTO(dtos, total);
    }

    // Book 엔티티를 BookDTO로 변환 (기존 메소드명 유지 가능, 또는 toBookDto 등으로 명확히)
    private BookDTO toDto(Book entity) {
        if (entity == null) return null; // Null 체크 추가
        return new BookDTO( // BookDTO의 생성자 BookDTO(Book book)을 사용하거나, 직접 필드 매핑
                entity.getBookIsbn(),
                entity.getBookTitle(),
                entity.getBookAuthor(),
                entity.getBookPublisher(),
                entity.getBookCover(),
                entity.getBookDescription(),
                entity.getBookPubdate() != null
                        ? entity.getBookPubdate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        : ""
        );
    }

    // BookDTO를 Book 엔티티로 변환 (기존 메소드명 유지 가능)
    private Book toEntity(BookDTO bookDTO) {
        if (bookDTO == null) return null; // Null 체크 추가
        LocalDate pubDate = null;
        String text = bookDTO.getBookPubdate();
        if (text != null && !text.isBlank()) {
            // pubDate 형식이 "YYYYMMDD" 또는 "YYYY-MM-DD" 등 다양할 수 있으므로,
            // BookDTO.fromApiResponse에서 파싱된 형식을 따르거나, 여기서 더 유연하게 파싱해야 할 수 있습니다.
            // 현재는 ISO_LOCAL_DATE ("YYYY-MM-DD")를 가정합니다.
            try {
                if (text.matches("\\d{8}")) { // YYYYMMDD 형식이라면
                    pubDate = LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
                } else if (text.matches("\\d{4}-\\d{2}-\\d{2}")) { // YYYY-MM-DD 형식이라면
                    pubDate = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                } else if (!text.isEmpty()){
                    // 다른 형식이나 빈 문자열이 아닌 경우, 일단 로깅하고 null 처리 (혹은 예외)
                    logger.warn("알 수 없는 날짜 형식입니다: {}, ISBN: {}", text, bookDTO.getBookIsbn());
                }
            } catch (Exception e) {
                logger.warn("날짜 파싱 중 오류 발생: {}, 입력값: {}, ISBN: {}", e.getMessage(), text, bookDTO.getBookIsbn());
                pubDate = null; // 파싱 실패 시 null 처리
            }
        }
        return Book.builder()
                .bookIsbn(bookDTO.getBookIsbn())
                .bookTitle(bookDTO.getBookTitle())
                .bookAuthor(bookDTO.getBookAuthor())
                .bookPublisher(bookDTO.getBookPublisher())
                .bookCover(bookDTO.getBookCover())
                .bookDescription(bookDTO.getBookDescription())
                .bookPubdate(pubDate)
                .build();
    }

    // ❗ [수정된 부분] 메소드 이름 변경 및 Null 처리 추가
    // @Transactional(readOnly = true) // DB 조회만 하므로 readOnly=true 가능
    public BookDTO getBookDetailsByIsbn(String bookIsbn) { // 메소드 이름 변경
        if (bookIsbn == null || bookIsbn.trim().isEmpty()) {
            logger.warn("ISBN이 null이거나 비어있습니다.");
            return null;
        }
        Book foundBook = bookRepository.findByBookIsbn(bookIsbn);
        if (foundBook == null) {
            logger.info("DB에서 ISBN '{}'에 해당하는 책을 찾을 수 없습니다. 외부 API 조회를 시도할 수 있습니다 (현재는 미구현).", bookIsbn);
            // TODO: 필요하다면 여기서 externalClient.fetchBooks(bookIsbn, 1, 1) 등을 호출하여
            // 외부 API에서 책 정보를 가져와 DB에 저장하고 반환하는 로직을 추가할 수 있습니다.
            // 현재는 DB에 없으면 null을 반환합니다.
            return null;
        }
        // return new BookDTO(foundBook); // BookDTO 생성자를 사용하거나 toDto 메소드 사용
        return toDto(foundBook); // 일관성을 위해 toDto 메소드 사용
    }
}