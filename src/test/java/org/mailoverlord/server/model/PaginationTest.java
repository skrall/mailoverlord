package org.mailoverlord.server.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Test for Pagination logic.
 */
class PaginationTest {

    /**
     * Builds a page of {@code totalPages} pages, with a page size of one, so that
     * page numbers line up exactly with the number of pages.
     */
    private static Pagination pagination(int pageNumber, int totalPages) {
        return new Pagination(new PageImpl<>(List.of(), PageRequest.of(pageNumber, 1), totalPages));
    }

    @Test
    void pageOneOfTen() {
        Pagination pagination = pagination(0, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(5);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(1);
        assertThat(pagination.isFirstPage()).as("is first page").isTrue();
        assertThat(pagination.isLastPage()).as("is last page").isFalse();
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(6);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isTrue();
        assertThat(pagination.getTotalPages()).as("total pages").isEqualTo(10);
    }

    @Test
    void pageTwoOfTen() {
        Pagination pagination = pagination(1, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(5);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(2);
        assertThat(pagination.isFirstPage()).as("is first page").isFalse();
        assertThat(pagination.isLastPage()).as("is last page").isFalse();
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(6);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isTrue();
    }

    @Test
    void pageThreeOfTen() {
        Pagination pagination = pagination(2, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(5);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(3);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(6);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isTrue();
    }

    @Test
    void pageFourOfTen() {
        Pagination pagination = pagination(3, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(2);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(6);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(4);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(7);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isTrue();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isTrue();
    }

    @Test
    void pageSevenOfTen() {
        Pagination pagination = pagination(6, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(5);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(9);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(7);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(4);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(10);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isTrue();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isTrue();
    }

    @Test
    void pageEightOfTen() {
        Pagination pagination = pagination(7, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(6);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(10);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(8);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(5);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(10);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isTrue();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
    }

    @Test
    void pageNineOfTen() {
        Pagination pagination = pagination(8, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(6);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(10);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(9);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(5);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(10);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isTrue();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
    }

    @Test
    void lastPageOfTen() {
        Pagination pagination = pagination(9, 10);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(6);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(10);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(10);
        assertThat(pagination.isFirstPage()).as("is first page").isFalse();
        assertThat(pagination.isLastPage()).as("is last page").isTrue();
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(5);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(10);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isTrue();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
    }

    @Test
    void onlyPage() {
        Pagination pagination = pagination(0, 1);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(1);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(1);
        assertThat(pagination.isFirstPage()).as("is first page").isTrue();
        assertThat(pagination.isLastPage()).as("is last page").isTrue();
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(1);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
        assertThat(pagination.getTotalPages()).as("total pages").isEqualTo(1);
    }

    @Test
    void pageOneOfTwo() {
        Pagination pagination = pagination(0, 2);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(2);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(1);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(2);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
    }

    @Test
    void pageTwoOfTwo() {
        Pagination pagination = pagination(1, 2);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(2);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(2);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(2);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
    }

    @Test
    void lastPageOfFour() {
        Pagination pagination = pagination(3, 4);

        assertThat(pagination.getStartPageNumber()).as("start page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isEqualTo(4);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(4);
        assertThat(pagination.getPreviousPageLinkNumber()).as("previous page link number").isEqualTo(1);
        assertThat(pagination.getNextPageLinkNumber()).as("next page link number").isEqualTo(4);
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
    }

    /**
     * An empty result set reports no pages at all, so the template must not try to
     * render a page number range that runs backwards.
     */
    @Test
    void emptyResultHasNoPages() {
        Pagination pagination = pagination(0, 0);

        assertThat(pagination.getTotalPages()).as("total pages").isEqualTo(0);
        assertThat(pagination.getCurrentPageNumber()).as("current page number").isEqualTo(1);
        assertThat(pagination.getEndPageNumber()).as("end page number").isZero();
        assertThat(pagination.isDisplayPreviousPageLink()).as("display previous page link").isFalse();
        assertThat(pagination.isDisplayNextPageLink()).as("display next page link").isFalse();
    }
}
