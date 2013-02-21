package org.mailoverlord.server.model;

import org.junit.Test;
import org.springframework.data.domain.Page;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test for Pagination logic.
 */
public class PaginationTest {

    @Test
    public void testPageOneOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(0);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(true);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 1, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 5, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 1, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", true, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 6, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", false, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", true, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageTwoOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(1);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 1, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 5, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 2, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 6, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", false, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", true, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageThreeOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(2);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 1, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 5, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 3, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 6, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", false, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", true, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageFourOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(3);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 2, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 6, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 4, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 7, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", true, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", true, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageSevenOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(6);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 5, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 9, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 7, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 4, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 10, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", true, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", true, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageEightOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(7);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 6, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 10, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 8, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 5, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 10, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", true, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", false, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageNineOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(8);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 6, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 10, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 9, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 5, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 10, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", true, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", false, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageTenOfTen() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(9);
        given(page.getTotalPages()).willReturn(10);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(true);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 6, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 10, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 10, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", true, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 5, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 10, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", true, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", false, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 10, pagination.getTotalPages());
    }

    @Test
    public void testPageOneOfOne() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(0);
        given(page.getTotalPages()).willReturn(1);
        given(page.isFirstPage()).willReturn(true);
        given(page.isLastPage()).willReturn(true);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 1, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 1, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 1, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", true, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", true, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 1, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", false, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", false, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 1, pagination.getTotalPages());
    }

    @Test
    public void testPageOneOfTwo() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(0);
        given(page.getTotalPages()).willReturn(2);
        given(page.isFirstPage()).willReturn(true);
        given(page.isLastPage()).willReturn(false);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 1, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 2, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 1, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", true, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", false, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 2, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", false, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", false, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 2, pagination.getTotalPages());
    }

    @Test
    public void testPageTwoOfTwo() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(1);
        given(page.getTotalPages()).willReturn(2);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(true);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 1, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 2, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 2, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", true, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 2, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", false, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", false, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 2, pagination.getTotalPages());
    }

    @Test
    public void testPageFourOfFour() {
        Page page = mock(Page.class);
        given(page.getNumber()).willReturn(3);
        given(page.getTotalPages()).willReturn(4);
        given(page.isFirstPage()).willReturn(false);
        given(page.isLastPage()).willReturn(true);

        Pagination pagination = new Pagination(page);

        assertEquals("Page start number does not match", 1, pagination.getStartPageNumber());
        assertEquals("Page end number does not match", 4, pagination.getEndPageNumber());
        assertEquals("Selected page does not match", 4, pagination.getCurrentPageNumber());
        assertEquals("Is First Page does not match", false, pagination.isFirstPage());
        assertEquals("Is Last Page does not match", true, pagination.isLastPage());
        assertEquals("Previous Page Link Number does not match", 1, pagination.getPreviousPageLinkNumber());
        assertEquals("Next Page Link Number does not match", 4, pagination.getNextPageLinkNumber());
        assertEquals("Is Display Previous Page Link does not match", false, pagination.isDisplayPreviousPageLink());
        assertEquals("Is Display Next Page Link does not match", false, pagination.isDisplayNextPageLink());
        assertEquals("Total Pages does not match", 4, pagination.getTotalPages());
    }
}
