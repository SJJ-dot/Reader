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

    var anchors = document.querySelectorAll('a[href]');
    var chapters = [];
    var seen = new Set();

    // 启发式寻找章节列表容器
    var containers = document.querySelectorAll('div, ul, section, dl');
    var bestContainer = null;
    var maxLinks = 0;

    for (var i = 0; i < containers.length; i++) {
        var c = containers[i];
        if (!visible(c)) continue;
        var links = c.querySelectorAll('a[href]');
        var count = 0;
        for (var j = 0; j < links.length; j++) {
            var t = (links[j].innerText || links[j].textContent || '').trim();
            if (t.length > 1 && (/\d+/.test(t) || t.indexOf('第') >= 0 || t.indexOf('章') >= 0)) {
                count++;
            }
        }
        if (count > maxLinks) {
            maxLinks = count;
            bestContainer = c;
        }
    }

    var targetLinks = bestContainer ? bestContainer.querySelectorAll('a[href]') : anchors;
    for (var i = 0; i < targetLinks.length; i++) {
        var a = targetLinks[i];
        var t = (a.innerText || a.textContent || '').trim();
        var href = abs(a.getAttribute('href'));
        if (!href || href === '#' || /^javascript:/i.test(href) || seen.has(href)) continue;

        if (t.length > 1) {
            chapters.push({ title: t, url: href });
            seen.add(href);
        }
    }

    // 目录下一页
    var nextPage = '';
    var nextKeys = ['下一页', '下一頁', '下一页>', '下一页 »', 'Next', 'Next Page'];
    for (var k = 0; k < anchors.length; k++) {
        var a = anchors[k];
        var t = (a.innerText || a.textContent || '').trim();
        if (nextKeys.indexOf(t) >= 0 || t.endsWith('下一页')) {
            nextPage = abs(a.getAttribute('href'));
            break;
        }
    }

    return JSON.stringify({
        chapters: chapters,
        nextPage: nextPage
    });
})();

