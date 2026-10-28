package com.project.lol.webview.injections

/*
 * Holding the player heart opens the same save-to-playlist panel as a short
 * tap, by clicking the page's own heart button.
 */

object HeartLongPress {
    const val CONTENT = """
        (function () {
            if (window.__splHeartLP) return;
            window.__splHeartLP = true;

            var OPEN_MS = 450;
            var t = null, fired = false, sx = 0, sy = 0, suppressUntil = 0;

            function heart(e){ return e.target && e.target.closest ? e.target.closest('#spl-liked') : null; }
            function clear(){ if (t) { clearTimeout(t); t = null; } }

            document.addEventListener('pointerdown', function(e){
                if (!heart(e)) return;
                if (e.pointerType === 'mouse' && e.button !== 0) return;
                fired = false; sx = e.clientX; sy = e.clientY;
                clear();
                t = setTimeout(function(){
                    t = null; fired = true;
                    suppressUntil = Date.now() + 600;
                    try { if (navigator.vibrate) navigator.vibrate(12); } catch(err){}
                    var fb = (typeof window.splFavBtn === 'function') ? window.splFavBtn() : null;
                    if (fb) fb.click();
                }, OPEN_MS);
            }, { capture: true, passive: true });

            document.addEventListener('pointermove', function(e){
                if (t && (Math.abs(e.clientX - sx) > 10 || Math.abs(e.clientY - sy) > 10)) clear();
            }, { capture: true, passive: true });
            document.addEventListener('pointerup', clear, { capture: true, passive: true });
            document.addEventListener('pointercancel', clear, { capture: true, passive: true });

            document.addEventListener('click', function(e){
                if (Date.now() < suppressUntil && heart(e)) { e.preventDefault(); e.stopPropagation(); }
            }, true);
            document.addEventListener('contextmenu', function(e){
                if ((fired || t) && heart(e)) e.preventDefault();
            }, true);

            setInterval(function(){
                try {
                    if (window.__splAddDump) return;
                    var root = document.querySelector('#context-menu') || document.querySelector('[role="dialog"]');
                    if (!root || !/playlist/i.test(root.textContent || '')) return;
                    window.__splAddDump = true;
                    var rows = root.querySelectorAll('li,[role="menuitemcheckbox"],[role="menuitem"],[role="option"],button');
                    var info = [];
                    for (var i = 0; i < Math.min(rows.length, 6); i++) {
                        var r = rows[i], cs = getComputedStyle(r);
                        info.push(r.tagName + (r.getAttribute('role') ? '[' + r.getAttribute('role') + ']' : '') + ' h=' + Math.round(r.getBoundingClientRect().height) + ' pad=' + cs.paddingTop + '/' + cs.paddingBottom + ' mar=' + cs.marginTop + '/' + cs.marginBottom + ' txt=' + (r.textContent || '').trim().slice(0, 18));
                    }
                    AndBridge.dbg('d', 'addpl-dom root=' + root.tagName + (root.id ? '#' + root.id : '') + ' rows=' + rows.length + ' | ' + info.join(' | ') + ' | html=' + root.outerHTML.replace(/<svg[\s\S]*?<\/svg>/g, '<svg>').replace(/<img[^>]*>/g, '<img>').slice(0, 1500));
                } catch(e){}
            }, 500);
        })();
    """
}
