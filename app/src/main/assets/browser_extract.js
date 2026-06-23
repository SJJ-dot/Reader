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

    /**
     * \u7528\u4E8E\u5173\u952E\u5B57\u5339\u914D\u7684\u5F52\u4E00\u5316\uFF1A\u5C0F\u5199\u5316 + \u628A\u5BB9\u6613\u88AB\u7AD9\u70B9\u66FF\u6362\u7684\u5B57\u7B26\u56DE\u5199\u5230\u6807\u51C6\u5F62\u5F0F\u3002
     * \u771F\u5B9E\u7AD9\u70B9\u91CC\u5E38\u89C1 "\u4E0B\u2014\u9875"/"\u4E0B\uFF0D\u9875"/"\u4E0B_\u9875" \u7B49\u7528\u7834\u6298\u53F7/\u8FDE\u5B57\u7B26\u5192\u5145"\u4E00"\u7684\u5199\u6CD5\u3002
     */
    function normalize(s) {
        if (!s) return '';
        return s.toLowerCase()
            // \u5404\u79CD dash / \u8FDE\u5B57\u7B26 / \u4E0B\u5212\u7EBF / \u7A7A\u767D \u2192 \u4E00
            .replace(/[\u2010-\u2015\uFF0D\-_\s]/g, '\u4E00');
    }

    // 1. 标题
    var title = '';
    // 优先尝试获取 og:title
    var ogTitle = document.querySelector('meta[property="og:title"]');
    if (ogTitle) {
        title = ogTitle.getAttribute('content') || '';
    }
    // 尝试 keywords meta 标签
    if (!title) {
        var keywords = document.querySelector('meta[name="keywords"]');
        if (keywords) {
            title = keywords.getAttribute('content') || '';
        }
    }
    // 如果 title 不存在，尝试获取 h1
    if (!title) {
        var h1 = document.querySelector('h1');
        if (h1 && visible(h1)) title = textOf(h1);
    }
    // 尝试 h2/h3
    if (!title) {
        var hs = document.querySelectorAll('h2,h3');
        for (var i = 0; i < hs.length; i++) {
            if (visible(hs[i])) { title = textOf(hs[i]); break; }
        }
    }
    // 最后使用 document.title
    if (!title) title = document.title || '';
    if(title) title = title.split(/[-|_,]/)[0].trim(); // 去掉网站名等后缀

    // 2. 正文：常见小说网站容器优先，支持单元素与多元素（如 p 列表），最后启发式选择 textLength 最大的可见块
    var content = '';
    var selectors = [
        '.booktxt', '.content', '.con', '.txtnav', '.read_chapterDetail', '.contents','.chaptercontent','.novelcontent',
        '#booktxt', '#content', '#con', '#txtnav', '#read_chapterDetail', '#contents','#chaptercontent','#novelcontent',
        'main'
    ];
    for (var s = 0; s < selectors.length; s++) {
        var els;
        try { els = document.querySelectorAll(selectors[s]); } catch (e) { continue; }
        if (!els || els.length === 0) continue;
        var parts = [];
        for (var ei = 0; ei < els.length; ei++) {
            var node = els[ei];
            if (!visible(node)) continue;
            var t = textOf(node);
            if (t) parts.push(t);
        }
        var joined = parts.join('\n\n').trim();
        if (joined.length > 100) {
            content = joined;
            break;
        }
    }
    if (!content) {
        // 启发式：找文本最长的可见块
        var candidates = document.querySelectorAll('div,section,article,main');
        var best = null, bestLen = 0;
        for (var c = 0; c < candidates.length; c++) {
            var cand = candidates[c];
            if (!visible(cand)) continue;
            var ct = textOf(cand);
            if (ct.length > bestLen) { best = cand; bestLen = ct.length; }
        }
        if (best && bestLen > 200) content = textOf(best);
        else if (document.body) content = textOf(document.body);
    }

    // 3. 上一章 / 下一章 链接：优先精确选择器，其次扫描 a 标签按文本/id/class 匹配
    var prev = '', next = '', prevText = '', nextText = '';

    function pickLink(sel) {
        try {
            var el = document.querySelector(sel);
            if (!el) return null;
            // 若选中的是容器，则取其中第一个有效的 a[href]
            var a = el.tagName === 'A' ? el : el.querySelector('a[href]');
            if (!a) return null;
            var href = a.getAttribute('href') || '';
            if (!href || href === '#' || /^javascript:/i.test(href)) return null;
            return { href: abs(href), text: (a.innerText || a.textContent || '').trim() };
        } catch (e) { return null; }
    }

    var nextSelectors = [
        '#next_url', '#chapternext',
        '.next_url', '.chapternext',
        "a[rel='next']", "a[rel*='next']"
    ];
    var prevSelectors = [
      '#prev_url', '#chapterprev','#prenext',
      '.prev_url', '.chapterprev','.prenext',
        "a[rel='prev']", "a[rel*='prev']"
    ];
    for (var ns = 0; ns < nextSelectors.length; ns++) {
        var pn = pickLink(nextSelectors[ns]);
        if (pn) { next = pn.href; nextText = pn.text; break; }
    }
    for (var ps = 0; ps < prevSelectors.length; ps++) {
        var pp = pickLink(prevSelectors[ps]);
        if (pp) { prev = pp.href; prevText = pp.text; break; }
    }

    var prevKeys = ['上頁', '上页', '上一页', '上一节', '上一頁', 'prev', 'previous','上一章', '上章'];
    var nextKeys = [ '下頁', '下页', '下一页', '下一节', '下一頁', 'next', '下一章','下章'];
    var anchors = document.querySelectorAll('a[href]');
    if (!prev || !next) {
        for (var k = 0; k < anchors.length; k++) {
            var a = anchors[k];
            var rawText = (a.innerText || a.textContent || '').trim();
            var t = normalize(rawText);
            var id = normalize(a.id || '');
            var cls = normalize((a.className || '').toString());
            var href = a.getAttribute('href') || '';
            if (!href || href === '#' || /^javascript:/i.test(href)) continue;
            if (!prev) {
                for (var p = 0; p < prevKeys.length; p++) {
                    var pk = normalize(prevKeys[p]);
                    if (t.indexOf(pk) >= 0 || id.indexOf(pk) >= 0 || cls.indexOf(pk) >= 0) {
                        prev = abs(href); prevText = rawText; break;
                    }
                }
            }
            if (!next) {
                for (var n = 0; n < nextKeys.length; n++) {
                    var nk = normalize(nextKeys[n]);
                    if (t.indexOf(nk) >= 0 || id.indexOf(nk) >= 0 || cls.indexOf(nk) >= 0) {
                        next = abs(href); nextText = rawText; break;
                    }
                }
            }
            if (prev && next) break;
        }
    }

    // 4. 目录链接
    var directory = '';
    var dirKeys = ['目录', '章节列表', '返回目录', '回目录', 'Index', 'Table of Contents'];
    for (var k = 0; k < anchors.length; k++) {
        var a = anchors[k];
        var t = (a.innerText || a.textContent || '').trim();
        var id = (a.id || '').toLowerCase();
        var cls = (a.className || '').toString().toLowerCase();
        var href = a.getAttribute('href') || '';
        if (!href || href === '#' || /^javascript:/i.test(href)) continue;
        for (var d = 0; d < dirKeys.length; d++) {
            if (t === dirKeys[d] || id.indexOf('chapterlist') >= 0 || id.indexOf('directory') >= 0) {
                directory = abs(href);
                break;
            }
        }
        if (directory) break;
    }

    // 5. 全部超链接（用于章节抽屉，过滤无标题与失效，按目标 URL 与标题分别去重）
    var allLinks = [];
    var urlSeen = new Set();
    var titleSeen = new Set();
    for (var li = 0; li < anchors.length; li++) {
        var la = anchors[li];
        if (!visible(la)) continue;
        var lhref = la.getAttribute('href') || '';
        if (!lhref || lhref === '#' || /^javascript:/i.test(lhref)) continue;
        var labs = abs(lhref);
        if (urlSeen.has(labs)) continue;
        var ltext = (la.innerText || la.textContent || '').trim();
        if (!ltext) continue;
        if (titleSeen.has(ltext)) continue;
        urlSeen.add(labs);
        titleSeen.add(ltext);
        allLinks.push({ title: ltext, url: labs });
    }

    // 6. prev/next 兜底：如果前面没找到，再从 allLinks 里做一次归一化匹配
    if (!prev || !next) {
        for (var ai = 0; ai < allLinks.length; ai++) {
            var item = allLinks[ai];
            var t = normalize(item.title || '');
            if (!prev) {
                for (var pi = 0; pi < prevKeys.length; pi++) {
                    if (t.indexOf(normalize(prevKeys[pi])) >= 0) {
                        prev = item.url; prevText = item.title; break;
                    }
                }
            }
            if (!next) {
                for (var ni = 0; ni < nextKeys.length; ni++) {
                    if (t.indexOf(normalize(nextKeys[ni])) >= 0) {
                        next = item.url; nextText = item.title; break;
                    }
                }
            }
            if (prev && next) break;
        }
    }

    return JSON.stringify({
        title: title,
        content: content,
        prev: prev,
        prevText: prevText,
        next: next,
        nextText: nextText,
        directory: directory,
        allLinks: allLinks
    });
})();
