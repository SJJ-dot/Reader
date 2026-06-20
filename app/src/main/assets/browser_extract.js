(function () {
    function abs(href) {
        try {
            return new URL(href, location.href).href;
        } catch (e) {
            return href || '';
        }
    }

    function visible(el) {
        if (!el) return false;
        var s = window.getComputedStyle(el);
        if (!s) return false;
        if (s.display === 'none' || s.visibility === 'hidden') return false;
        return true;
    }

    function textOf(el) {
        if (!el) return '';
        var t = el.innerText || el.textContent || '';
        // 规范换行/空白
        t = t.replace(/\u00A0/g, ' ');
        t = t.replace(/[\t\f\v]+/g, ' ');
        t = t.replace(/[ ]{2,}/g, ' ');
        t = t.replace(/\n{3,}/g, '\n\n');
        return t.trim();
    }

    // 1. 标题
    var title = '';
    var h1 = document.querySelector('h1');
    if (h1 && visible(h1)) title = textOf(h1);
    if (!title) {
        var hs = document.querySelectorAll('h2,h3');
        for (var i = 0; i < hs.length; i++) {
            if (visible(hs[i])) { title = textOf(hs[i]); break; }
        }
    }
    if (!title) title = document.title || '';

    // 2. 正文：常见小说网站容器优先，其次启发式选择 textLength 最大的可见块
    var contentEl = null;
    var selectors = [
        '#chaptercontent', '#content', '#booktext', '#TextContent', '#htmlContent',
        '.chapter-content', '.content', '.read-content', '.article-content',
        '.novel-content', '.bookText', '#nr1', '#nr', '#BookText',
        'article'
    ];
    for (var s = 0; s < selectors.length; s++) {
        var el = document.querySelector(selectors[s]);
        if (el && visible(el) && textOf(el).length > 100) { contentEl = el; break; }
    }
    if (!contentEl) {
        // 启发式：找文本最长的可见块
        var candidates = document.querySelectorAll('div,section,article,main');
        var best = null, bestLen = 0;
        for (var c = 0; c < candidates.length; c++) {
            var node = candidates[c];
            if (!visible(node)) continue;
            var t = textOf(node);
            if (t.length > bestLen) { best = node; bestLen = t.length; }
        }
        if (best && bestLen > 200) contentEl = best;
    }
    var content = '';
    if (contentEl) {
        content = textOf(contentEl);
    } else if (document.body) {
        content = textOf(document.body);
    }

    // 3. 上一章 / 下一章 链接：扫描 a 标签，根据文本与常见 id/class
    var prev = '', next = '', prevText = '', nextText = '';
    var prevKeys = ['上一章', '上一頁','上章', '上頁', '上页', '上一页', '上一节', 'prev', 'previous', '\u4e0a\u7ae0'];
    var nextKeys = ['下一章', '下一頁','下章', '下頁', '下页', '下一页', '下一节', 'next', '\u4e0b\u7ae0'];
    var anchors = document.querySelectorAll('a[href]');
    for (var k = 0; k < anchors.length; k++) {
        var a = anchors[k];
        var t = (a.innerText || a.textContent || '').trim();
        var id = (a.id || '').toLowerCase();
        var cls = (a.className || '').toString().toLowerCase();
        var href = a.getAttribute('href') || '';
        if (!href || href === '#' || /^javascript:/i.test(href)) continue;
        var matchPrev = false, matchNext = false;
        for (var p = 0; p < prevKeys.length; p++) {
            if (t.indexOf(prevKeys[p]) >= 0 || id.indexOf(prevKeys[p]) >= 0 || cls.indexOf(prevKeys[p]) >= 0) {
                matchPrev = true; break;
            }
        }
        for (var n = 0; n < nextKeys.length; n++) {
            if (t.indexOf(nextKeys[n]) >= 0 || id.indexOf(nextKeys[n]) >= 0 || cls.indexOf(nextKeys[n]) >= 0) {
                matchNext = true; break;
            }
        }
        if (matchPrev && !prev) {
            prev = abs(href);
            prevText = t;
        }
        if (matchNext && !next) {
            next = abs(href);
            nextText = t;
        }
        if (prev && next) break;
    }

    return JSON.stringify({
        title: title,
        content: content,
        prev: prev,
        prevText: prevText,
        next: next,
        nextText: nextText
    });
})();
