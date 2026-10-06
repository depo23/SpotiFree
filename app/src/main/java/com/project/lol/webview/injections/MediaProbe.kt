package com.project.lol.webview.injections

/**
 * Test-build only: logs what the web player renders for videos and podcasts
 * (tag js.info, prefix "[probe]") so the full-screen player can target the
 * real DOM. Injected only while logging is enabled. Not for upstream.
 */
object MediaProbe {
    const val CONTENT = """
        (function(){
            if(window.__splProbeOn) return;
            window.__splProbeOn=true;
            var last='';
            function cls(el){ if(!el) return ''; var c=(el.className&&el.className.baseVal!==undefined)?el.className.baseVal:(el.className||''); return String(c).split(/\s+/).slice(0,3).join('.'); }
            function tid(el){ return el?(el.getAttribute('data-testid')||''):''; }
            function path(el){ var out=[];for(var i=0;el&&i<5;i++,el=el.parentElement){ out.push((el.tagName||'').toLowerCase()+(tid(el)?'['+tid(el)+']':'')+(cls(el)?'.'+cls(el):'')); } return out.join(' < '); }
            function snap(){
                var s={};
                s.url=location.pathname;
                var rc=document.querySelector('#Desktop_PanelContainer_Id');
                s.npv=rc&&rc.parentNode&&rc.parentNode.parentNode?String(rc.parentNode.parentNode.ariaHidden):'none';
                s.videos=[].slice.call(document.querySelectorAll('video')).map(function(v){
                    var r=v.getBoundingClientRect();
                    return {path:path(v),rs:v.readyState,paused:v.paused,vw:v.videoWidth,vh:v.videoHeight,box:Math.round(r.width)+'x'+Math.round(r.height),t:Math.round(v.currentTime||0),src:(v.currentSrc||'').slice(0,40)};
                });
                s.vpc=!!document.querySelector('.VideoPlayer__container');
                var npw=document.querySelector('[data-testid="now-playing-widget"]');
                s.widget=npw?[].slice.call(npw.querySelectorAll('[data-testid]')).map(tid).filter(function(x,i,a){return a.indexOf(x)===i}):null;
                s.links=[].slice.call(document.querySelectorAll('[data-testid="now-playing-widget"] a[href], aside[data-testid="now-playing-bar"] a[href]')).map(function(a){return tid(a)+'='+a.getAttribute('href')}).slice(0,6);
                var bar=document.querySelector('aside[data-testid="now-playing-bar"]');
                s.buttons=bar?[].slice.call(bar.querySelectorAll('button')).map(function(b){return (tid(b)||'-')+':'+(b.getAttribute('aria-label')||'')+(b.disabled||b.getAttribute('aria-disabled')==='true'?'(off)':'')}):null;
                var t={};[].slice.call(document.querySelectorAll('[data-testid]')).forEach(function(e){var x=tid(e);if(/video|canvas|thumb|episode|show|speed|chapter|podcast|npv/i.test(x))t[x]=(t[x]||0)+1;});
                s.testids=t;
                var c={};[].slice.call(document.querySelectorAll('[class*="ideo"],[class*="anvas"]')).forEach(function(e){var k=cls(e);if(k)c[k]=(c[k]||0)+1;});
                s.classes=c;
                return s;
            }
            function tick(){
                try{
                    var j=JSON.stringify(snap());
                    if(j===last) return;
                    last=j;
                    for(var i=0;i<j.length;i+=1500) AndBridge.dbg('i','[probe] '+(i?'(cont) ':'')+j.slice(i,i+1500));
                }catch(e){ try{AndBridge.dbg('w','[probe] error '+e)}catch(_){} }
            }
            setInterval(tick,2000);
            tick();
        })();
    """
}
