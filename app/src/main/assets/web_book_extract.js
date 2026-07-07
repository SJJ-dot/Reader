(function () {
  function abs(href) {
    try {
      return new URL(href, location.href).href;
    } catch (e) {
      return href || '';
    }
  }

  function pickImageUrl(img) {
    if (!img) return '';
    var attrs = [
      'currentSrc',
      'src',
      'data-src',
      'data-original',
      'data-lazy-src',
      'data-ks-lazyload',
      'data-url',
      'data-image',
    ];
    for (var i = 0; i < attrs.length; i++) {
      var key = attrs[i];
      var value = img[key] || (img.getAttribute && img.getAttribute(key)) || '';
      if (value) return abs(String(value).split(' ')[0].trim());
    }
    var srcset = (img.getAttribute && img.getAttribute('srcset')) || '';
    if (srcset) {
      var first = srcset.split(',')[0].trim().split(' ')[0].trim();
      if (first) return abs(first);
    }
    return '';
  }

  function keywordBonus(text) {
    text = (text || '').toLowerCase();
    var score = 0;
    if (/cover|fmimg|book|novel|poster/.test(text)) score += 55;
    if (/avatar|icon|logo|sprite|banner|qrcode|ad/.test(text)) score -= 90;
    return score;
  }

  function computeSize(img) {
    var rect = img.getBoundingClientRect ? img.getBoundingClientRect() : null;
    var w = rect && rect.width ? Math.round(rect.width) : 0;
    var h = rect && rect.height ? Math.round(rect.height) : 0;
    if (!w && img.naturalWidth) w = img.naturalWidth;
    if (!h && img.naturalHeight) h = img.naturalHeight;
    return { w: w || 0, h: h || 0 };
  }

  var title = (document.title || '').trim();
  var images = document.querySelectorAll('img');
  var best = Object.create(null);

  for (var idx = 0; idx < images.length; idx++) {
    var img = images[idx];
    var url = pickImageUrl(img);
    if (!url) continue;

    var lower = url.toLowerCase();
    if (lower.endsWith('.svg') || lower.indexOf('.svg?') >= 0) continue;

    var sz = computeSize(img);
    if (sz.w < 40 || sz.w > 300) continue;
    if (sz.h <= sz.w || sz.h >= 3*sz.w) continue;

    var text = [
      img.getAttribute('alt') || '',
      img.getAttribute('title') || '',
      img.id || '',
      img.className || '',
      img.parentElement ? (img.parentElement.className || '') : '',
      url,
    ].join(' ');

    var score = 100 + Math.max(0, 80 - idx * 5) + keywordBonus(text);
    if (sz.w > 0 && sz.h > 0) {
      var ratio = sz.w / sz.h;
      score += Math.max(-60, 50 - Math.abs(ratio - 0.72) * 100);
    }

    var old = best[url];
    if (!old || old.score < score) {
      best[url] = {
        url: url,
        source: sz.w+'x'+sz.h,
        score: Math.round(score),
        width: sz.w,
        height: sz.h,
      };
    }
  }

  try {
    var og = document.querySelector(
      'meta[property="og:image"],meta[name="og:image"],meta[property="twitter:image"],meta[name="twitter:image"]'
    );
    if (og) {
      var ogUrl = abs((og.getAttribute('content') || '').trim());
      if (ogUrl) {
        best[ogUrl] = {
          url: ogUrl,
          source: '页面推荐',
          score: 2000,
          width: 0,
          height: 0,
        };
      }
    }
  } catch (e) {}

  var list = [];
  for (var key in best) {
    if (Object.prototype.hasOwnProperty.call(best, key)) list.push(best[key]);
  }
  list.sort(function (a, b) {
    return (b.score || 0) - (a.score || 0);
  });
  if (list.length > 18) list = list.slice(0, 18);

  return JSON.stringify({
    title: title,
    coverCandidates: list,
  });
})();
