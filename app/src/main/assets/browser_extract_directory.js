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
    var allLinks = [];
    var seen = new Set();
    for (var i = 0; i < anchors.length; i++) {
        var a = anchors[i];
        if (!visible(a)) continue;
        var href = abs(a.getAttribute('href'));
        if (!href || href === '#' || /^javascript:/i.test(href) || seen.has(href)) continue;
        var t = (a.innerText || a.textContent || '').trim();
        if (!t) continue;
        seen.add(href);
        allLinks.push({ title: t, url: href });
    }

    return JSON.stringify({
        allLinks: allLinks
    });
})();
