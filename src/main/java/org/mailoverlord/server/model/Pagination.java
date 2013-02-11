package org.mailoverlord.server.model;

import org.springframework.data.domain.Page;

/**
 * Calculates pagination information.
 */
public class Pagination {

    private Page<?> page;

    private boolean isFirstPage;

    private boolean isLastPage;

    private int startPageNumber;

    private int endPageNumber;

    private int currentPageNumber;

    private int previousPageLinkNumber;

    private int nextPageLinkNumber;

    public Pagination(Page<?> page) {
        this.page = page;
        init();
    }

    private void init() {
        isFirstPage = page.isFirstPage();
        isLastPage = page.isLastPage();
        currentPageNumber = page.getNumber() + 1;

        if (currentPageNumber <= 3) {
            previousPageLinkNumber = 1;
            startPageNumber = 1;
            endPageNumber = Math.min(page.getTotalPages(), 5);
            nextPageLinkNumber = Math.min(page.getTotalPages(), 6);
            return;
        }
        if ((page.getTotalPages() - currentPageNumber) <= 2) {
            startPageNumber = Math.max(1, (page.getTotalPages() - 4));
            previousPageLinkNumber = Math.max(1, startPageNumber - 1);
            endPageNumber = page.getTotalPages();
            nextPageLinkNumber = page.getTotalPages();
            return;
        }

        startPageNumber = currentPageNumber - 2;
        previousPageLinkNumber = Math.max(1, startPageNumber - 1);
        endPageNumber = currentPageNumber + 2;
        nextPageLinkNumber = Math.min(page.getTotalPages(), endPageNumber + 1);
    }

    public boolean isFirstPage() {
        return isFirstPage;
    }

    public boolean isLastPage() {
        return isLastPage;
    }

    public int getStartPageNumber() {
        return startPageNumber;
    }

    public int getEndPageNumber() {
        return endPageNumber;
    }

    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    public int getPreviousPageLinkNumber() {
        return previousPageLinkNumber;
    }

    public int getNextPageLinkNumber() {
        return nextPageLinkNumber;
    }

    public boolean isDisplayPreviousPageLink() {
        return !(previousPageLinkNumber == startPageNumber);
    }

    public boolean isDisplayNextPageLink() {
        return !(nextPageLinkNumber == endPageNumber);
    }
}
