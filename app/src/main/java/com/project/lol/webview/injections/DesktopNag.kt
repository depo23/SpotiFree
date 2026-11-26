package com.project.lol.webview.injections

/*
 * Hides Spotify's "Download Spotify for Windows" prompt (banner, toast or dialog).
 * Matched by installer link, by buttons seen opening the installer, or by text. Elements
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

            var STOP = 'body,html,main,aside,nav,header,footer,#main,#main-view,#root,[data-testid=root],[data-testid=main],section,[role=main],[role=navigation],[role=menu],ul,ol';
            var KEEP = 'a[href*="download.scdn.co"],a[href*="SpotifySetup"],a[href*="spotify.com/download"],a[href$="/download"]';

            /* Largest ancestor that is still just this card: no other links, little text,
               never a page-level container or a list. Stops one level below the first
               container that also holds unrelated content. */
            function cardOf(link){
                var card = link;
                for(var d=0; d<10; d++){
                    var p = card.parentElement;
                    if(!p || p.matches(STOP)) break;
                    if((p.textContent||'').length > 300) break;
                    var other = p.querySelectorAll('a[href]');
                    var foreign = false;
                    for(var i=0;i<other.length;i++){ if(!other[i].matches(KEEP)){ foreign = true; break; } }
                    if(foreign || p.querySelector('img[src*="scdn.co/image"],[data-testid=tracklist-row],[data-testid=card-click-handler]')) break;
                    card = p;
                }
                return card;
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
                el.setAttribute('data-spl-nag','1');
                el.style.setProperty('display','none','important');
            }

            /* Buttons that open the installer via window.open have no href to match.
               The click that triggers it is caught, its card hidden, and a selector for
               the button remembered so the same banner is hidden on sight next time. */
            var SETUP = /download\.scdn\.co|SpotifySetup|spotify\.com\/(\w+\/)?download/i;
            try{ localStorage.removeItem('spl_nag_sel'); }catch(e){}
            var lastTarget = null;
            document.addEventListener('pointerdown', function(e){ lastTarget = e.target; }, true);
            document.addEventListener('click', function(e){ lastTarget = e.target; }, true);

            function learned(){ try{ return JSON.parse(localStorage.getItem('spl_nag_sel2')||'[]').filter(function(x){ return x && x.s; }); }catch(e){ return []; } }
            function selectorFor(btn){
                var tid = btn.getAttribute('data-testid');
                if(tid) return btn.tagName.toLowerCase() + '[data-testid="' + tid + '"]';
                var cls = String(btn.className||'').trim().split(/\s+/).filter(Boolean);
                return cls.length ? btn.tagName.toLowerCase() + '.' + cls.join('.') : null;
            }
            /* Never hide page content: anything holding artwork, track rows, cards or a
               page section, anything with lots of text, or anything taller than 60% of the
               screen unless it is a dialog. */
            function safe(el){
                if(!el || el.matches(STOP)) return false;
                if((el.textContent||'').length > 600) return false;
                if(el.querySelector('img[src*="scdn.co/image"],[data-testid=tracklist-row],[data-testid=card-click-handler],[data-testid=home-page],main,section,[role=grid],[role=list]')) return false;
                var role = el.getAttribute('role') || '';
                if(/dialog/.test(role)) return true;
                return el.getBoundingClientRect().height < window.innerHeight * 0.6;
            }
            /* The card, or the fixed/sticky overlay that holds it; null when neither is safe to hide. */
            function containerOf(btn){
                var card = cardOf(btn);
                for(var p = card; p && !p.matches(STOP); p = p.parentElement){
                    var pos = getComputedStyle(p).position;
                    if((pos === 'fixed' || pos === 'sticky') && safe(p)) return p;
                }
                return safe(card) ? card : null;
            }
            function hideFromButton(btn, why){
                var box = containerOf(btn);
                if(!box){ try{ AndBridge.dbg('s','desktop-app ' + why + ' skipped, container unsafe: ' + describe(cardOf(btn))); }catch(e){} return; }
                if(box.closest('[data-spl-nag]') || box.hasAttribute('data-spl-nag-ok')) return;
                try{ AndBridge.dbg('s','desktop-app ' + why + ': ' + describe(box)); }catch(e){}
                dismiss(box);
                hide(box);
            }

            var origOpen = window.open;
            window.open = function(url){
                if(url && SETUP.test(String(url))){
                    try{
                        var btn = lastTarget && lastTarget.closest && lastTarget.closest('button,a,[role=button]');
                        try{ AndBridge.dbg('s','desktop-app installer open trapped: button=' + (btn ? describe(btn) : 'none') +
                            ' card=' + (btn ? describe(cardOf(btn)) : 'none') + ' safe=' + !!(btn && containerOf(btn))); }catch(e){}
                        if(btn && containerOf(btn)){
                            /* Class lists are shared by every button of a style, so the
                               button's own text is stored too and must match. */
                            var sel = selectorFor(btn), txt = (btn.textContent||'').trim(), list = learned();
                            var known = list.some(function(x){ return x.s === sel && x.t === txt; });
                            if(sel && !known){ list.push({s: sel, t: txt}); localStorage.setItem('spl_nag_sel2', JSON.stringify(list.slice(-10))); }
                            hideFromButton(btn, 'button hidden on click');
                        }
                    }catch(e){}
                    return null;
                }
                return origOpen.apply(window, arguments);
            };

            /* A hidden box that later fills with page content (the web player renders
               home inside the same wrapper after the banner) is shown again. Height is
               not checked: a hidden box has none. */
            function recheckHidden(){
                var hidden = document.querySelectorAll('[data-spl-nag]');
                for(var i=0;i<hidden.length;i++){
                    var el = hidden[i];
                    if((el.textContent||'').length > 600 || el.querySelector('img[src*="scdn.co/image"],[data-testid=tracklist-row],[data-testid=card-click-handler],[data-testid=home-page],main,section,[role=grid],[role=list]')){
                        el.removeAttribute('data-spl-nag');
                        el.style.removeProperty('display');
                        el.setAttribute('data-spl-nag-ok','1');
                        try{ AndBridge.dbg('s','desktop-app hidden box holds page content, shown again: ' + describe(el)); }catch(e){}
                    }
                }
            }

            function sweep(){
                timer = 0;
                try{
                    recheckHidden();
                    var sels = learned();
                    for(var k=0;k<sels.length;k++){
                        var hits = [];
                        try{ hits = document.querySelectorAll(sels[k].s); }catch(e){}
                        for(var h=0;h<hits.length;h++){
                            if((hits[h].textContent||'').trim() === sels[k].t) hideFromButton(hits[h], 'learned button hidden');
                        }
                    }
                    var links = document.querySelectorAll(KEEP);
                    for(var i=0;i<links.length;i++){
                        if(links[i].closest('[data-spl-nag]')) continue;
                        var card = cardOf(links[i]);
                        if(card.hasAttribute('data-spl-nag-ok')) continue;
                        try{ AndBridge.dbg('s','desktop-app card hidden: ' + describe(card)); }catch(e){}
                        dismiss(card);
                        hide(card);
                    }
                    var els = document.querySelectorAll(CANDIDATES);
                    for(var j=0;j<els.length;j++){
                        var el = els[j];
                        if(el.closest('[data-spl-nag]') || el.hasAttribute('data-spl-nag-ok')) continue;
                        var t = el.textContent || '';
                        if(t.length > 400) continue;
                        var d = describe(el);
                        if(!seen[d]){ seen[d] = 1; try{ AndBridge.dbg('s','popup seen: ' + d); }catch(e){} }
                        if(!NAG.test(t)) continue;
                        dismiss(el);
                        hide(el);
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
