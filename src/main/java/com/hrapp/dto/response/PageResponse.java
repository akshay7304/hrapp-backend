package com.hrapp.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic wrapper around a Spring Data {@link Page} that flattens the parts a
 * mobile / web client actually needs. Use {@link #from(Page)} to build one
 * from any service repository result.
 * <p>
 * Two of the boolean fields ({@code isFirst} / {@code isLast}) carry explicit
 * {@code @JsonProperty} hints because Jackson's default behavior would strip
 * the {@code is} prefix and serialize them as {@code first} / {@code last};
 * the explicit names match the spec.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    @JsonProperty("isFirst")
    private boolean isFirst;

    @JsonProperty("isLast")
    private boolean isLast;

    private boolean hasNext;
    private boolean hasPrevious;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
