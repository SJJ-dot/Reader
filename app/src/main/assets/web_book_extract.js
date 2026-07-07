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
    if (sz.w < 60 || sz.w > 230) continue;
    if (sz.h <= sz.w || sz.h >= 2*sz.w) continue;

    var text = [
      img.getAttribute('alt') || '',
      img.getAttribute('title') || '',
      img.id || '',
      img.className || '',
      img.parentElement ? (img.parentElement.className || '') : '',
      url,
    ].join(' ');

    best[url] = {
            url: url,
            source: sz.w+'x'+sz.h,
            width: sz.w,
            height: sz.h,
          };
  }

  var list = [];
  for (var key in best) {
    if (Object.prototype.hasOwnProperty.call(best, key)) list.push(best[key]);
  }

  return JSON.stringify({
    title: title,
    coverCandidates: list,
  });
})();
