package com.project.lol.webview.injections

/**
 * Keeps the web player's own "Videos and Canvas" settings in line with Spotilol's
 * Canvas / Videos switches. With them off, Spotify serves no Canvas and no episode
 * video at all. Applied once per change: opens /preferences in-app, flips the
 * toggles that differ, then goes back. Expects window.__splWebPrefs=[canvas,videos].
 */
object WebPrefs {
    const val CONTENT = """
        (function(){
            if(window.splSyncWebPrefs) return;
            function log(m){ try{ AndBridge.dbg('i','[probe] webprefs '+m); }catch(e){} }
            var busy=false;
            window.splSyncWebPrefs=function(canvas,videos,force){
                var want=(canvas?'c1':'c0')+(videos?'v1':'v0');
                var done=null;
                try{ done=localStorage.getItem('splWebPrefs'); }catch(e){}
                if((!force&&done===want)||busy) return;
                busy=true;
                var back=location.pathname!=='/preferences';
                if(back){
                    history.pushState({},'','/preferences');
                    window.dispatchEvent(new PopStateEvent('popstate',{state:null}));
                }
                var tries=0;
                var iv=setInterval(function(){
                    var boxes=[].slice.call(document.querySelectorAll('input[type=checkbox][id^="settings.videos-and-canvas."]'));
                    if(!boxes.length&&++tries<40) return;
                    clearInterval(iv);
                    if(boxes.length){
                        log('before '+boxes.map(function(b){ return b.id+'='+b.checked; }).join(','));
                        boxes.forEach(function(b){
                            var on=b.id.slice(-7)==='.canvas'?canvas:videos;
                            if(b.checked!==on) b.click();
                        });
                        try{ localStorage.setItem('splWebPrefs',want); }catch(e){}
                    } else log('toggles not found');
                    setTimeout(function(){ if(back) history.back(); busy=false; },400);
                },250);
            };
            // First run: wait until signed in, then apply the saved switches.
            var waited=0;
            var boot=setInterval(function(){
                if(document.querySelector('[data-testid="user-widget-link"]')){
                    clearInterval(boot);
                    var p=window.__splWebPrefs||[true,true];
                    window.splSyncWebPrefs(p[0],p[1],false);
                } else if(++waited>60) clearInterval(boot);
            },2000);
        })();
    """
}
