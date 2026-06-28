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

    function linkTitleText(text) {
        if (!text) return '';
        return String(text)
            .replace(/\u00A0/g, ' ')
            .replace(/[\s\t\f\v\r\n]+/g, ' ')
            .trim();
    }

    /**
     * \u7528\u4E8E\u5173\u952E\u5B57\u5339\u914D\u7684\u5F52\u4E00\u5316\uFF1A\u5C0F\u5199\u5316 + \u628A\u5BB9\u6613\u88AB\u7AD9\u70B9\u66FF\u6362\u7684\u5B57\u7B26\u56DE\u5199\u5230\u6807\u51C6\u5F62\u5F0F\u3002
     * \u771F\u5B9E\u7AD9\u70B9\u91CC\u5E38\u89C1 "\u4E0B\u2014\u9875"/"\u4E0B\uFF0D\u9875"/"\u4E0B_\u9875" \u7B49\u7528\u7834\u6298\u53F7/\u8FDE\u5B57\u7B26\u5192\u5145"\u4E00"\u7684\u5199\u6CD5\u3002
     */
    function normalize(s) {
        if (!s) return '';
        return s.toLowerCase()
            .replace(/[\u2010-\u2015\uFF0D\-_\s\u30FC]/g, '');
    }

    function matchNavKeyword(values, keys) {
        for (var vi = 0; vi < values.length; vi++) {
            var value = values[vi] || '';
            for (var ki = 0; ki < keys.length; ki++) {
                var key = normalize(keys[ki]);
                if (value.indexOf(key) >= 0) return keys[ki];
            }
        }
        return '';
    }

    function matchLooseChapterNav(text, aria, direction) {
        var t = normalize(text || '');
        var a = normalize(aria || '');
        var re = direction === 'next' ? /下.{0,6}章/ : /上.{0,6}章/;
        if (re.test(t) || re.test(a)) return direction === 'next' ? '下*章' : '上*章';
        return '';
    }

    // 1. 标题
    var title = '';
    // 优先尝试获取 og:title
    var ogTitle = document.querySelector('meta[property="og:title"]');
    if (ogTitle) {
        title = ogTitle.getAttribute('content') || '';
    }

    if (!title) {
        title = document.title || '';
    }

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

    // 诊断信息：记录每个阶段是否命中，便于排查"上滑加载下一页"等无显式链接场景
    var _debug = {
        prevHit: 'none',
        nextHit: 'none',
        prevSelector: '',
        nextSelector: '',
        prevKeyword: '',
        nextKeyword: '',
        anchorsTotal: 0,
        prevCandidates: [],   // 含 prev 关键字的 anchor 文本（前 10 个）
        nextCandidates: [],   // 含 next 关键字的 anchor 文本（前 10 个）
        infiniteScrollHints: [],
        scriptHints: [],      // <script> 内联里命中 next/prev/url 关键字的片段
        scriptTemplate: {},   // prevId/nextId + chapterUrl 模板解析结果
        windowVars: {},       // window 上 next/prev 相关全局变量
        htmlText: ''          // 缺少导航时输出完整 HTML
    };

    function invalidNavText(text) {
        return /已经是(?:最后|最前|第一)(?:一)?章|没有(?:上一|下一)(?:章|页)|返回目录|目录/.test(text || '');
    }

    function sameHost(url) {
        try {
            return new URL(url, location.href).host === location.host;
        } catch (e) {
            return false;
        }
    }

    function setNavFromScriptTemplate() {
        try {
            var scripts = document.querySelectorAll('script:not([src])');
            var prevId = '', nextId = '', chapterUrl = '';
            for (var si = 0; si < scripts.length; si++) {
                var txt = scripts[si].textContent || '';
                if (!txt) continue;
                if (!prevId) {
                    var pm = txt.match(/prevId\s*[:=]\s*["']([^"']*)["']/i);
                    if (pm) prevId = pm[1];
                }
                if (!nextId) {
                    var nm = txt.match(/nextId\s*[:=]\s*["']([^"']*)["']/i);
                    if (nm) nextId = nm[1];
                }
                if (!chapterUrl) {
                    var cm = txt.match(/(?:book\.)?chapterUrl\s*=\s*["']([^"']*chapterId[^"']*)["']/i);
                    if (cm) chapterUrl = cm[1];
                }
            }
            if (!chapterUrl || chapterUrl.indexOf('chapterId') < 0) return;
            _debug.scriptTemplate = { prevId: prevId, nextId: nextId, chapterUrl: chapterUrl };
            if (prevId && (!prev || invalidNavText(prevText))) {
                var prevFromScript = abs(chapterUrl.replace(/chapterId/g, prevId));
                if (sameHost(prevFromScript)) {
                    prev = prevFromScript;
                    prevText = invalidNavText(prevText) ? '上一章' : (prevText || '上一章');
                    _debug.prevHit = 'scriptTemplate';
                }
            }
            if (nextId && (!next || invalidNavText(nextText))) {
                var nextFromScript = abs(chapterUrl.replace(/chapterId/g, nextId));
                if (sameHost(nextFromScript)) {
                    next = nextFromScript;
                    nextText = invalidNavText(nextText) ? '下一章' : (nextText || '下一章');
                    _debug.nextHit = 'scriptTemplate';
                }
            }
            if (invalidNavText(prevText) && !_debug.scriptTemplate.prevId) {
                prev = '';
                prevText = '';
                _debug.prevHit = 'invalidTextCleared';
            }
            if (invalidNavText(nextText) && !_debug.scriptTemplate.nextId) {
                next = '';
                nextText = '';
                _debug.nextHit = 'invalidTextCleared';
            }
        } catch (e) {
            _debug.scriptTemplate = { err: String(e && e.message || e) };
        }
    }

    function pickLink(sel) {
        try {
            var el = document.querySelector(sel);
            if (!el) return null;
            // 若选中的是容器，则取其中第一个有效的 a[href]
            var a = el.tagName === 'A' ? el : el.querySelector('a[href]');
            if (!a) return null;
            var href = a.getAttribute('href') || '';
            if (!href || href === '#' || /^javascript:/i.test(href)) return null;
            return { href: abs(href), text: linkTitleText(a.innerText || a.textContent || '') };
        } catch (e) { return null; }
    }

    var nextSelectors = [
        '#next_url', '#chapternext', '#pb_next', '#pn_next', '#nextLink',
        '.next_url', '.chapternext', '.url_next', '.pb_next',
        'li.next > a[href]', 'a.next[href]',
        "a[rel='next']", "a[rel*='next']"
    ];
    var prevSelectors = [
        '#prev_url', '#chapterprev', '#prenext', '#pb_prev', '#pn_prev', '#prevLink',
        '.prev_url', '.chapterprev', '.prenext', '.url_prev', '.url_pre', '.pb_prev',
        'li.prev > a[href]', 'li.pre > a[href]', 'a.prev[href]', 'a.pre[href]',
        "a[rel='prev']", "a[rel*='prev']"
    ];
    for (var ns = 0; ns < nextSelectors.length; ns++) {
        var pn = pickLink(nextSelectors[ns]);
        if (pn) {
            next = pn.href; nextText = pn.text;
            _debug.nextHit = 'selector'; _debug.nextSelector = nextSelectors[ns];
            break;
        }
    }
    for (var ps = 0; ps < prevSelectors.length; ps++) {
        var pp = pickLink(prevSelectors[ps]);
        if (pp) {
            prev = pp.href; prevText = pp.text;
            _debug.prevHit = 'selector'; _debug.prevSelector = prevSelectors[ps];
            break;
        }
    }

    // 同时覆盖单数形式 ("pre"/"nxt") 与中文常见词
    var prevKeys = ['上頁', '上页', '上一页', '上一节', '上一頁', '上一章', '上章', 'previous', 'prev', 'pre'];
    var nextKeys = ['下頁', '下页', '下一页', '下一节', '下一頁', '下一章', '下章', 'next', 'nxt'];
    var anchors = document.querySelectorAll('a[href]');
    _debug.anchorsTotal = anchors.length;
    // 同时把 a 的祖先（li/span/div 等）类名 / id 也纳入匹配，覆盖
    // <li class="prev"><a class="url_pre" id="pb_prev"> 这种把语义放在父节点上的写法。
    function ancestorTokens(el) {
        var tokens = '';
        var cur = el && el.parentElement;
        var depth = 0;
        while (cur && depth < 3) {
            tokens += ' ' + (cur.id || '') + ' ' + ((cur.className || '').toString());
            cur = cur.parentElement;
            depth++;
        }
        return normalize(tokens);
    }

    if (!prev || !next) {
        for (var k = 0; k < anchors.length; k++) {
            var a = anchors[k];
            var rawText = linkTitleText(a.innerText || a.textContent || '');
            var t = normalize(rawText);
            var id = normalize(a.id || '');
            var cls = normalize((a.className || '').toString());
            var aria = normalize(a.getAttribute('aria-label') || a.getAttribute('title') || '');
            var anc = ancestorTokens(a);
            var href = a.getAttribute('href') || '';
            if (!href || href === '#' || /^javascript:/i.test(href)) continue;
            if (!prev) {
                var prevHit = matchNavKeyword([t, id, cls, aria, anc], prevKeys) || matchLooseChapterNav(rawText, aria, 'prev');
                if (prevHit) {
                    prev = abs(href); prevText = rawText;
                    _debug.prevHit = 'keyword'; _debug.prevKeyword = prevHit;
                    if (_debug.prevCandidates.length < 10) {
                        _debug.prevCandidates.push({ text: rawText, href: abs(href), key: prevHit });
                    }
                }
            }
            if (!next) {
                var nextHit = matchNavKeyword([t, id, cls, aria, anc], nextKeys) || matchLooseChapterNav(rawText, aria, 'next');
                if (nextHit) {
                    next = abs(href); nextText = rawText;
                    _debug.nextHit = 'keyword'; _debug.nextKeyword = nextHit;
                    if (_debug.nextCandidates.length < 10) {
                        _debug.nextCandidates.push({ text: rawText, href: abs(href), key: nextHit });
                    }
                }
            }
            if (prev && next) break;
        }
    }

    // 7. 启发式：探测"上滑加载"等无显式链接的特征，输出诊断信息
    try {
        // 收集 anchor 的所有 attribute（特别是 data-*、onclick），便于发现真实下一章 URL
        function dumpAttrs(el) {
            var out = {};
            if (!el || !el.attributes) return out;
            for (var ai = 0; ai < el.attributes.length; ai++) {
                var at = el.attributes[ai];
                // 限长，避免日志爆炸
                var v = (at.value || '').toString();
                if (v.length > 200) v = v.slice(0, 200) + '...';
                out[at.name] = v;
            }
            // onclick handler 文本（getAttribute 可能拿不到 IDL 属性）
            if (typeof el.onclick === 'function') {
                var fn = el.onclick.toString();
                out['__onclickFn'] = fn.length > 300 ? fn.slice(0, 300) + '...' : fn;
            }
            return out;
        }

        // 常见的无限滚动容器/标记
        var infinSel = [
            '[data-infinite]', '[data-infinite-scroll]', '.infinite-scroll', '.scroll-load',
            '#loadmore', '.loadmore', '.load-more', '.next-page-loading', '.loading-more'
        ];
        for (var isi = 0; isi < infinSel.length; isi++) {
            if (document.querySelector(infinSel[isi])) {
                _debug.infiniteScrollHints.push('selector:' + infinSel[isi]);
            }
        }
        // 全文搜索常见提示语
        var bodyText = (document.body && (document.body.innerText || '')).slice(0, 4000);
        var hintWords = ['上滑', '上滑加载', '下拉加载', '滚动加载', '加载更多', '正在加载', 'loading more', 'load more'];
        for (var hi = 0; hi < hintWords.length; hi++) {
            if (bodyText.indexOf(hintWords[hi]) >= 0) {
                _debug.infiniteScrollHints.push('text:' + hintWords[hi]);
            }
        }
        // 当 next 还是空，把含有 next 关键字（哪怕仅在 class/id/aria）但 href 为空/javascript 的候选也列出来
        if (!next) {
            for (var dk = 0; dk < anchors.length; dk++) {
                var da = anchors[dk];
                var drt = (da.innerText || da.textContent || '').trim();
                var dnt = normalize(drt);
                var did = normalize(da.id || '');
                var dcl = normalize((da.className || '').toString());
                var hit = matchNavKeyword([dnt, did, dcl], nextKeys) || matchLooseChapterNav(drt, '', 'next');
                if (hit && _debug.nextCandidates.length < 10) {
                    _debug.nextCandidates.push({
                        text: drt,
                        href: da.getAttribute('href') || '',
                        id: da.id || '',
                        cls: (da.className || '').toString(),
                        key: hit,
                        attrs: dumpAttrs(da)
                    });
                }
            }
        }
        if (!prev) {
            for (var dk2 = 0; dk2 < anchors.length; dk2++) {
                var da2 = anchors[dk2];
                var drt2 = (da2.innerText || da2.textContent || '').trim();
                var dnt2 = normalize(drt2);
                var did2 = normalize(da2.id || '');
                var dcl2 = normalize((da2.className || '').toString());
                var hit2 = matchNavKeyword([dnt2, did2, dcl2], prevKeys) || matchLooseChapterNav(drt2, '', 'prev');
                if (hit2 && _debug.prevCandidates.length < 10) {
                    _debug.prevCandidates.push({
                        text: drt2,
                        href: da2.getAttribute('href') || '',
                        id: da2.id || '',
                        cls: (da2.className || '').toString(),
                        key: hit2,
                        attrs: dumpAttrs(da2)
                    });
                }
            }
        }

        // 检查 window 上的常见全局变量（不少站点把下一章 URL 直接挂在 window 上）
        var winKeys = [
            'nextUrl', 'next_url', 'nextpage', 'next_page', 'nextChapterUrl',
            'prevUrl', 'prev_url', 'prevpage', 'prev_page', 'prevChapterUrl',
            'chapterNext', 'chapterPrev', 'nextLink', 'prevLink',
            'nextId', 'prevId', 'NEXT_URL', 'PREV_URL'
        ];
        for (var wi = 0; wi < winKeys.length; wi++) {
            try {
                var v = window[winKeys[wi]];
                if (v !== undefined && v !== null && typeof v !== 'function') {
                    var sv = String(v);
                    if (sv && sv.length < 500) _debug.windowVars[winKeys[wi]] = sv;
                }
            } catch (e) { }
        }

        // 扫描 inline <script>：抓含 next/prev/chapter/url 关键字的赋值/字符串
        try {
            var scripts = document.querySelectorAll('script:not([src])');
            var scriptKeys = /(next[_-]?(?:url|page|chapter|id|link)|prev[_-]?(?:url|page|chapter|id|link)|chapter[_-]?(?:next|prev|url)|下一(?:章|页)|上一(?:章|页))/i;
            for (var si = 0; si < scripts.length && _debug.scriptHints.length < 10; si++) {
                var txt = scripts[si].textContent || '';
                if (!txt) continue;
                if (!scriptKeys.test(txt)) continue;
                // 抓所有匹配行（前后留一些上下文）
                var lines = txt.split(/\r?\n/);
                for (var li = 0; li < lines.length && _debug.scriptHints.length < 10; li++) {
                    if (scriptKeys.test(lines[li])) {
                        var ln = lines[li].trim();
                        if (ln.length > 300) ln = ln.slice(0, 300) + '...';
                        _debug.scriptHints.push(ln);
                    }
                }
            }
        } catch (e) {
            _debug.scriptHints.push('err:' + (e && e.message || e));
        }
    } catch (e) {
        _debug.infiniteScrollHints.push('err:' + (e && e.message || e));
    }

    // 通过 inline JS 的 prevId/nextId + chapterUrl 模板兜底，并修正"已经是最后一章"这类目录误匹配。
    setNavFromScriptTemplate();

    function shortText(v, maxLen) {
        var s = (v === undefined || v === null) ? '' : String(v);
        maxLen = maxLen || 180;
        return s.length > maxLen ? s.slice(0, maxLen) + '...' : s;
    }

    function attrsText(attrs) {
        if (!attrs) return '';
        var parts = [];
        for (var k in attrs) {
            if (Object.prototype.hasOwnProperty.call(attrs, k)) {
                parts.push(k + '=' + JSON.stringify(shortText(attrs[k], 120)));
            }
        }
        return parts.join(' ');
    }

    function addCandidateLines(lines, title, list) {
        if (!list || list.length === 0) {
            lines.push(title + ': 无');
            return;
        }
        lines.push(title + ': ' + list.length + ' 个');
        for (var i = 0; i < list.length; i++) {
            var item = list[i] || {};
            lines.push(
                '  #' + (i + 1) +
                ' key=' + shortText(item.key) +
                ' text=' + JSON.stringify(shortText(item.text, 80)) +
                ' href=' + shortText(item.href, 160) +
                ' id=' + shortText(item.id) +
                ' class=' + shortText(item.cls)
            );
            if (item.attrs) {
                lines.push('     attrs: ' + attrsText(item.attrs));
            }
        }
    }

    function buildDebugLines() {
        var lines = [];
        lines.push('页面: ' + location.href);
        lines.push('标题: ' + title);
        lines.push('正文长度: ' + content.length);
        lines.push('上一章: hit=' + _debug.prevHit + ' selector=' + _debug.prevSelector + ' keyword=' + _debug.prevKeyword + ' text=' + (prevText || '') + ' url=' + (prev || ''));
        lines.push('下一章: hit=' + _debug.nextHit + ' selector=' + _debug.nextSelector + ' keyword=' + _debug.nextKeyword + ' text=' + (nextText || '') + ' url=' + (next || ''));
        lines.push('链接统计: anchors=' + _debug.anchorsTotal);
        if (_debug.scriptTemplate && (_debug.scriptTemplate.prevId !== undefined || _debug.scriptTemplate.nextId !== undefined || _debug.scriptTemplate.chapterUrl)) {
            lines.push('脚本模板: prevId=' + (_debug.scriptTemplate.prevId || '') + ' nextId=' + (_debug.scriptTemplate.nextId || '') + ' chapterUrl=' + (_debug.scriptTemplate.chapterUrl || ''));
        } else if (_debug.scriptTemplate && _debug.scriptTemplate.err) {
            lines.push('脚本模板错误: ' + _debug.scriptTemplate.err);
        } else {
            lines.push('脚本模板: 未发现');
        }
        var winKeys = Object.keys(_debug.windowVars || {});
        if (winKeys.length > 0) {
            lines.push('window变量:');
            for (var wi = 0; wi < winKeys.length; wi++) {
                lines.push('  ' + winKeys[wi] + '=' + shortText(_debug.windowVars[winKeys[wi]], 180));
            }
        } else {
            lines.push('window变量: 无');
        }
        if (!prev || !next) {
            try {
                _debug.htmlText = (document.documentElement && document.documentElement.outerHTML) || '';
            } catch (e) {
                _debug.htmlText = 'err:' + (e && e.message || e);
            }
        }

        if (_debug.infiniteScrollHints && _debug.infiniteScrollHints.length > 0) {
            lines.push('滚动加载提示: ' + _debug.infiniteScrollHints.join(' | '));
        } else {
            lines.push('滚动加载提示: 无');
        }
        if (_debug.scriptHints && _debug.scriptHints.length > 0) {
            lines.push('脚本片段: ' + _debug.scriptHints.length + ' 条');
            for (var si = 0; si < _debug.scriptHints.length; si++) {
                lines.push('  #' + (si + 1) + ' ' + shortText(_debug.scriptHints[si], 260));
            }
        } else {
            lines.push('脚本片段: 无');
        }
        if (_debug.htmlText) {
            lines.push('HTML文本:');
            lines.push(_debug.htmlText);
        }
        addCandidateLines(lines, '上一章候选', _debug.prevCandidates);
        addCandidateLines(lines, '下一章候选', _debug.nextCandidates);
        return lines;
    }

    return JSON.stringify({
        title: title,
        content: content,
        prev: prev,
        next: next,
        _debug: buildDebugLines()
    });
})();
