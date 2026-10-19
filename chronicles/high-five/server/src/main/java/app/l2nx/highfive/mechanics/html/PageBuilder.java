/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.mechanics.html;

import app.l2nx.highfive.mechanics.html.formatters.DefaultFormatter;
import app.l2nx.highfive.mechanics.html.pagehandlers.DefaultPageHandler;
import app.l2nx.highfive.mechanics.html.styles.DefaultStyle;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * @author UnAfraid
 * @param <T>
 */
public class PageBuilder<T> {
    private final Collection<T> elements;
    private final int elementsPerPage;
    private final String bypass;
    private int currentPage = 0;
    private IPageHandler pageHandler = DefaultPageHandler.INSTANCE;
    private IBypassFormatter formatter = DefaultFormatter.INSTANCE;
    private IHtmlStyle style = DefaultStyle.INSTANCE;
    private IBodyHandler<T> bodyHandler;

    private PageBuilder(Collection<T> elements, int elementsPerPage, String bypass) {
        this.elements = elements;
        this.elementsPerPage = elementsPerPage;
        this.bypass = bypass;
    }

    public PageBuilder<T> currentPage(int currentPage) {
        this.currentPage = Math.max(currentPage, 0);
        return this;
    }

    public PageBuilder<T> bodyHandler(IBodyHandler<T> bodyHandler) {
        Objects.requireNonNull(bodyHandler, "Body Handler cannot be null!");
        this.bodyHandler = bodyHandler;
        return this;
    }

    public PageBuilder<T> pageHandler(IPageHandler pageHandler) {
        Objects.requireNonNull(pageHandler, "Page Handler cannot be null!");
        this.pageHandler = pageHandler;
        return this;
    }

    public PageBuilder<T> formatter(IBypassFormatter formatter) {
        Objects.requireNonNull(formatter, "Formatter cannot be null!");
        this.formatter = formatter;
        return this;
    }

    public PageBuilder<T> style(IHtmlStyle style) {
        Objects.requireNonNull(style, "Style cannot be null!");
        this.style = style;
        return this;
    }

    public PageResult build() {
        Objects.requireNonNull(bodyHandler, "Body was not set!");

        final StringBuilder pagerTemplate = new StringBuilder();
        final int pages = (elements.size() / elementsPerPage) + ((elements.size() % elementsPerPage) > 0 ? 1 : 0);
        if (pages > 1) {
            pageHandler.apply(bypass, currentPage, pages - 1, pagerTemplate, formatter, style);
        }

        if (currentPage > pages) {
            currentPage = pages - 1;
        }

        final StringBuilder sb = new StringBuilder();
        final int start = Math.max(elementsPerPage * currentPage, 0);
        bodyHandler.create(elements, pages, start, elementsPerPage, sb);

        return new PageResult(pages, pagerTemplate, sb);
    }

    public static <T> PageBuilder<T> newBuilder(Collection<T> elements, int elementsPerPage, String bypass) {
        return new PageBuilder<>(elements, elementsPerPage, bypass.trim());
    }

    public static <T> PageBuilder<T> newBuilder(T[] elements, int elementsPerPage, String bypass) {
        return new PageBuilder<>(Arrays.asList(elements), elementsPerPage, bypass.trim());
    }
}
