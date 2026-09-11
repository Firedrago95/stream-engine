package io.slice.stream.apiserver.category.presentation;

import io.slice.stream.apiserver.category.application.CategoryQueryService;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    @GetMapping("/weekly-ranking")
    public ResponseEntity<List<WeeklyCategoryResponse>> getWeeklyCategoryRanking() {
        return ResponseEntity.ok(categoryQueryService.getWeeklyCategoryRanking());
    }
}
