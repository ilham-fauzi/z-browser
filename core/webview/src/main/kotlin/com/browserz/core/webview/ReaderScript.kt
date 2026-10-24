package com.browserz.core.webview

/**
 * Finds the main article of the page (the container with the most paragraph text) and rebuilds the document
 * from plain text nodes and images only: no page script, style or ad markup survives. Returns "ok" or "none".
 */
internal const val READER_JS = """
(function () {
  try {
    var cands = document.querySelectorAll('article,main,[role=main],section,div');
    var best = null, bestScore = 0;
    for (var i = 0; i < cands.length; i++) {
      var c = cands[i], ps = c.querySelectorAll(':scope > p, :scope > * > p'), sc = 0;
      for (var j = 0; j < ps.length; j++) { var t = (ps[j].innerText || '').trim(); if (t.length > 40) sc += t.length; }
      if (c.tagName === 'ARTICLE') sc *= 1.5;
      if (sc > bestScore) { bestScore = sc; best = c; }
    }
    if (!best || bestScore < 200) return 'none';
    var title = document.title, nodes = best.querySelectorAll('h1,h2,h3,h4,p,li,blockquote,pre,img');
    var parts = [];
    for (var k = 0; k < nodes.length; k++) {
      var n = nodes[k], tag = n.tagName.toLowerCase();
      if (tag === 'img') {
        var src = n.currentSrc || n.src;
        if (src && (n.naturalWidth || 0) >= 200) parts.push(['img', src]);
      } else {
        var text = (n.innerText || '').trim();
        if (text.length > (tag === 'li' ? 1 : 20)) parts.push([tag, text]);
      }
    }
    document.head.innerHTML = '';
    var meta = document.createElement('meta'); meta.name = 'viewport'; meta.content = 'width=device-width,initial-scale=1';
    var style = document.createElement('style');
    style.textContent = ':root{color-scheme:light dark}body{max-width:42rem;margin:0 auto;padding:1.2rem;font:18px/1.6 serif;word-wrap:break-word}' +
      'h1,h2,h3,h4{font-family:sans-serif;line-height:1.25}img{max-width:100%;height:auto;display:block;margin:1rem 0}' +
      'blockquote{margin-left:0;padding-left:1rem;border-left:3px solid #8886}pre{white-space:pre-wrap}';
    document.head.appendChild(meta); document.head.appendChild(style);
    document.body.className = ''; document.body.removeAttribute('style'); document.body.innerHTML = '';
    var h = document.createElement('h1'); h.textContent = title; document.body.appendChild(h);
    for (var m = 0; m < parts.length; m++) {
      var el = document.createElement(parts[m][0]);
      if (parts[m][0] === 'img') el.src = parts[m][1]; else el.textContent = parts[m][1];
      document.body.appendChild(el);
    }
    window.scrollTo(0, 0);
    return 'ok';
  } catch (e) { return 'none'; }
})()
"""
