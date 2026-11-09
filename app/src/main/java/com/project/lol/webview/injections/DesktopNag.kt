package com.project.lol.webview.injections

/*
 * Hides Spotify's "Download Spotify for Windows" prompt (banner, toast or dialog).
 * Matched by text because the markup changes between web player releases. Elements
 * are hidden, not removed, so React's tree stays intact; a dismiss button is
 * clicked first so any scroll lock the dialog set is released.
 */
object DesktopNag {
    const val CONTENT = """
        (function(){
            if(window.__splDesktopNagInit) return;
            window.__splDesktopNagInit = true;

            var CANDIDATES = '[role=dialog],[role=alertdialog],[role=alert],[role=status],[role=banner],[data-encore-id=banner],[data-encore-id=modal],[data-testid*=banner],[data-testid*=toast],[data-testid*=modal],.notistack-Snackbar';
            var NAG = /(download|get|install)[^.]{0,40}(spotify|app)[^.]{0,30}(windows|desktop)|spotify (for|on) (windows|desktop)/i;
            var DL_LINK = 'a[href*="/download"],a[href*="spotify.com/download"],a[href*="microsoft.com"],a[href*="apps.apple.com"]';
            var seen = {};
            var timer = 0;

            function describe(el){
                var a = el.querySelectorAll('a[href]'), hrefs = [];
                for(var i=0;i<a.length&&i<3;i++) hrefs.push(a[i].getAttribute('href'));
                return el.tagName.toLowerCase() + '#' + (el.id||'') + '.' + String(el.className||'').replace(/\s+/g,'.') +
                    ' testid=' + (el.getAttribute('data-testid')||'') + ' encore=' + (el.getAttribute('data-encore-id')||'') +
                    ' role=' + (el.getAttribute('role')||'') + ' hrefs=' + hrefs.join(',') +
                    ' text=' + (el.textContent||'').trim().slice(0,80);
            }

            function dismiss(el){
                try{
                    var bs = el.querySelectorAll('button');
                    for(var i=0;i<bs.length;i++){
                        var l = (bs[i].getAttribute('aria-label')||'') + ' ' + (bs[i].textContent||'');
                        if(/close|dismiss|not now|no thanks|cancel/i.test(l)){ bs[i].click(); break; }
                    }
                }catch(e){}
            }

            function hide(el){
                var top = el;
                while(top.parentElement && top.parentElement !== document.body && top.parentElement.children.length === 1) top = top.parentElement;
                top.setAttribute('data-spl-nag','1');
                top.style.setProperty('display','none','important');
            }

            function sweep(){
                timer = 0;
                try{
                    var els = document.querySelectorAll(CANDIDATES);
                    for(var i=0;i<els.length;i++){
                        var el = els[i];
                        if(el.closest('[data-spl-nag]')) continue;
                        var t = el.textContent || '';
                        if(t.length > 400) continue;
                        var d = describe(el);
                        if(!seen[d]){ seen[d] = 1; try{ AndBridge.dbg('s','popup seen: ' + d); }catch(e){} }
                        if(!NAG.test(t) && !el.querySelector(DL_LINK)) continue;
                        dismiss(el);
                        hide(el);
                        try{ AndBridge.dbg('s','desktop-app prompt hidden'); }catch(e){}
                    }
                }catch(e){}
            }

            function schedule(){ if(!timer) timer = setTimeout(sweep, 300); }

            var start = function(){
                new MutationObserver(schedule).observe(document.documentElement, {childList:true, subtree:true});
                schedule();
            };
            if(document.documentElement) start();
            else document.addEventListener('DOMContentLoaded', start);
        })();
    """
}
