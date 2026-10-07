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
                var spl=document.getElementById('spotilolPlayerControls');
                s.player=(window.__splFullPlayer?'fullscreen':'other')+':'+(spl?(spl.className||'-')+(spl.style.display==='none'?' hidden':''):'none');
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
            // Can this WebView decrypt Widevine video (video podcasts), or only audio?
            try{
                var drm=[['video avc1','video/mp4; codecs="avc1.42E01E"'],['video vp9','video/webm; codecs="vp9"'],['audio','audio/mp4; codecs="mp4a.40.2"']];
                drm.forEach(function(d){
                    var cfg=d[0]==='audio'?{audioCapabilities:[{contentType:d[1]}]}:{videoCapabilities:[{contentType:d[1]}]};
                    navigator.requestMediaKeySystemAccess('com.widevine.alpha',[cfg]).then(function(){ AndBridge.dbg('i','[probe] drm '+d[0]+' ok'); },function(e){ AndBridge.dbg('i','[probe] drm '+d[0]+' fail '+e); });
                });
            }catch(e){ try{AndBridge.dbg('i','[probe] drm error '+e)}catch(_){} }

            // Debug > Test Video Embed: overlay the playing episode's video embed and
            // log, once a second for 60s, what the embed renders and whether video plays.
            window.splTestVideoEmbed=function(){
                function log(m){ try{ AndBridge.dbg('i','[probe] embed '+m); }catch(e){} }
                var a=document.querySelector('[data-testid="now-playing-widget"] a[data-testid="context-item-link"]');
                var m=a&&(a.getAttribute('href')||'').match(/\/episode\/([A-Za-z0-9]+)/);
                if(!m){ log('no episode playing'); try{AndBridge.deferMessage('Play a video podcast episode first');}catch(e){} return; }
                var old=document.getElementById('spl-embed-test'); if(old) old.remove();
                var wrap=document.createElement('div');
                wrap.id='spl-embed-test';
                wrap.style.cssText='position:fixed;inset:0;z-index:2147483647;background:#000;display:flex;flex-direction:column';
                var bar=document.createElement('div');
                bar.style.cssText='display:flex;align-items:center;justify-content:space-between;padding:10px 14px;color:#fff;font:600 14px sans-serif';
                bar.innerHTML='<span>Video embed test: tap play, wait ~20s</span>';
                var x=document.createElement('button');
                x.textContent='Close';
                x.style.cssText='background:#fff;color:#000;border:0;border-radius:16px;padding:6px 14px;font:600 13px sans-serif';
                bar.appendChild(x);
                var f=document.createElement('iframe');
                f.src='/embed/episode/'+m[1]+'/video';
                f.allow='autoplay; encrypted-media; fullscreen; picture-in-picture';
                f.style.cssText='flex:1;width:100%;border:0';
                wrap.appendChild(bar); wrap.appendChild(f);
                document.body.appendChild(wrap);
                log('open id='+m[1]);
                var n=0,lastJ='';
                var iv=setInterval(function(){
                    n++;
                    var s={n:n};
                    try{
                        var d=f.contentDocument;
                        if(!d){ s.doc='inaccessible'; }
                        else{
                            s.testids=[].slice.call(d.querySelectorAll('[data-testid]')).map(function(e){return e.getAttribute('data-testid')}).filter(function(v,i,a){return a.indexOf(v)===i}).join(',');
                            s.media=[].slice.call(d.querySelectorAll('video,audio')).map(function(v){return v.tagName+' '+v.videoWidth+'x'+v.videoHeight+' t='+Math.round(v.currentTime||0)+' paused='+v.paused+' rs='+v.readyState+' src='+(v.currentSrc||'').slice(0,30)});
                            s.text=(d.body&&d.body.innerText||'').replace(/\s+/g,' ').slice(0,120);
                        }
                    }catch(e){ s.err=String(e).slice(0,80); }
                    var j=JSON.stringify(s).replace(/"n":\d+,?/,'');
                    if(j!==lastJ){ lastJ=j; log(JSON.stringify(s)); }
                    if(n>=60){ clearInterval(iv); log('done'); }
                },1000);
                x.onclick=function(){ clearInterval(iv); log('closed by user at '+n+'s'); wrap.remove(); };
            };
        })();
    """
}
