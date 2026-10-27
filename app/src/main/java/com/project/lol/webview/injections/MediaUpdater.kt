package com.project.lol.webview.injections

object MediaUpdater {
    const val CONTENT = """
            window.splUpCoverUrl = function(url){
                if(!url) return null;
                return url.replace(/ab67616d0000[0-9a-f]{4}/i, 'ab67616d0000b273');
            };

            window.splFavBtn = function(){
                try{
                    var w = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                    if(w) return w;
                    var bar = document.querySelector('aside[data-testid="now-playing-bar"]') || document;
                    var cs = bar.querySelectorAll('button[aria-checked]');
                    for(var i = 0; i < cs.length; i++){
                        if(!/control-button/.test(cs[i].getAttribute('data-testid') || '')) return cs[i];
                    }
                    var ls = bar.querySelectorAll('button[aria-label]');
                    for(var j = 0; j < ls.length; j++){
                        if(/liked songs|add to|remove from|save to|\bsave\b|\blike\b/i.test(ls[j].getAttribute('aria-label') || '')) return ls[j];
                    }
                    return null;
                }catch(e){ return null; }
            };

            window.splMS = function(){
                try{
                    var m = navigator.mediaSession && navigator.mediaSession.metadata;
                    if(!m || !m.title) return null;
                    var best = null, bw = -1;
                    (m.artwork || []).forEach(function(a){
                        var w = parseInt((a.sizes || '').split('x')[0], 10) || 0;
                        if(w > bw){ bw = w; best = a.src; }
                    });
                    return { name: m.title, artist: m.artist || '', album: m.album || '', cover: best };
                }catch(e){ return null; }
            };

            window.splWidgetText = function(){
                try{
                    var w = document.querySelector('[data-testid="now-playing-widget"]');
                    if(!w) return null;
                    var out = [];
                    var els = w.querySelectorAll('*');
                    for(var i = 0; i < els.length && out.length < 2; i++){
                        var e = els[i];
                        if(e.children.length || e.closest('button')) continue;
                        var t = (e.textContent || '').trim();
                        if(!t || /^[0-9]+:[0-9]{2}$/.test(t) || out.indexOf(t) !== -1) continue;
                        out.push(t);
                    }
                    if(!out.length) return null;
                    return { name: out[0], artist: out[1] || '' };
                }catch(e){ return null; }
            };

            window.splResolveTrackId = function(){
                if(window.splTrackId) return window.splTrackId;
                var ms = window.splMS();
                var meta = window.__splTrackMeta;
                if(!ms || !meta) return null;
                for(var k in meta){
                    var m = meta[k];
                    if(!m || m.name !== ms.name) continue;
                    if(ms.artist && m.artist && ms.artist.indexOf(m.artist) === -1) continue;
                    if(ms.album && m.album && m.album !== ms.album) continue;
                    return k;
                }
                return null;
            };

            window.splPlayingTrackId = function(){
                try{
                    var a = document.querySelector('[data-testid="now-playing-widget"] a[href*="/track/"]');
                    if(a){
                        var m = (a.getAttribute('href')||'').match(/\/track\/([a-zA-Z0-9]{22})/);
                        if(m) return m[1];
                    }
                    if(document.querySelector('a[data-testid="context-item-info-show"]')) return null;
                    return window.splResolveTrackId() || window.__curTrackId || null;
                }catch(e){ return null; }
            };

            window.splMergeTrackMeta = function(id, patch){
                if(!id || !patch) return null;
                window.__splTrackMeta = window.__splTrackMeta || {};
                var cur = window.__splTrackMeta[id];
                if(!cur){ cur = { uri: 'spotify:track:' + id }; window.__splTrackMeta[id] = cur; }
                for(var k in patch){ if(patch[k] != null && cur[k] == null) cur[k] = patch[k]; }
                return cur;
            };

            window.splWidgetMeta = function(id){
                try{
                    var w = document.querySelector('[data-testid="now-playing-widget"]');
                    if(!w) return null;
                    var a = w.querySelector('a[href*="/track/"]');
                    var m = a ? ((a.getAttribute('href')||'').match(/\/track\/([a-zA-Z0-9]{22})/)) : null;
                    if(!m || m[1] !== id) return null;
                    var out = {};
                    var t = (a.textContent||'').trim();
                    if(t) out.name = t;
                    var al = w.querySelector('a[data-testid="context-item-info-artist"]');
                    if(al){ var s = (al.textContent||'').trim(); if(s) out.artist = s; }
                    var im = w.querySelector('img[data-testid="cover-art-image"]');
                    if(im && im.src) out.cover = window.splUpCoverUrl(im.src);
                    return out;
                }catch(e){ return null; }
            };

            window.splRowMeta = function(id){
                try{
                    var onAlbumPage = location.pathname.indexOf('/album/') !== -1;
                    var rows = document.querySelectorAll('div[data-testid="tracklist-row"]');
                    for(var i=0;i<rows.length;i++){
                        var row = rows[i];
                        if(row.closest && row.closest('.playlistRecommenderContainer, [data-testid="recommended-track"]')) continue;
                        var link = row.querySelector('a[data-testid="internal-track-link"]') || row.querySelector('a[href*="/track/"]');
                        if(!link) continue;
                        if((link.getAttribute('href')||'').indexOf(id) === -1) continue;
                        var out = {};
                        var t = (link.textContent||'').trim();
                        if(t) out.name = t;
                        var arts = [];
                        var as = row.querySelectorAll('a[href*="/artist/"]');
                        for(var j=0;j<as.length;j++){
                            var nm = (as[j].textContent||'').trim();
                            if(nm && arts.indexOf(nm) === -1) arts.push(nm);
                        }
                        if(arts.length) out.artist = arts.join(', ');
                        var al = row.querySelector('a[href*="/album/"]');
                        if(al){ var an = (al.textContent||'').trim(); if(an) out.album = an; }
                        var img = row.querySelector('img');
                        if(img && img.src) out.cover = window.splUpCoverUrl(img.src);
                        if(!out.album && onAlbumPage){
                            var h1 = document.querySelector('main h1');
                            if(h1){ var hn = (h1.textContent||'').trim(); if(hn) out.album = hn; }
                            if(!out.cover){
                                var og = document.querySelector('meta[property="og:image"]');
                                if(og){ var oc = og.getAttribute('content'); if(oc) out.cover = window.splUpCoverUrl(oc); }
                            }
                        }
                        return out;
                    }
                }catch(e){}
                return null;
            };

            window.splEnsureTrackMeta = function(id){
                if(!id) return Promise.resolve();
                window.__splMetaRuns = window.__splMetaRuns || {};
                window.__splTrackMeta = window.__splTrackMeta || {};
                var existing = window.__splTrackMeta[id] || null;
                if(existing && existing.album != null) return Promise.resolve();
                if(window.__splMetaRuns[id]) return window.__splMetaRuns[id];

                var sigOf = function(m){ return m ? [(m.name||''),(m.artist||''),(m.album||''),(m.cover||'')].join('|') : ''; };
                var lastSig = sigOf(existing);

                var promote = function(){
                    if(window.splPlayingTrackId() !== id) return;
                    var m = window.__splTrackMeta && window.__splTrackMeta[id];
                    var s = sigOf(m);
                    if(s === lastSig) return;
                    lastSig = s;
                    window.__curTrackId = id;
                    window.__curTrackUri = 'spotify:track:' + id;
                    if(m && m.name) window.__curTrackName = m.name;
                    if(m && m.artist) window.__curTrackArtist = m.artist;
                    if(m && m.album) window.__curTrackAlbum = m.album;
                    if(m && m.cover) window.__curTrackCover = m.cover;
                    if(window.updMedia && typeof track !== 'undefined'){ try{ window.updMedia(); }catch(e){} }
                };

                var attempt = function(n, resolve){
                    try{
                        var wm = window.splWidgetMeta(id);
                        if(wm) window.splMergeTrackMeta(id, wm);
                        var rm = window.splRowMeta(id);
                        if(rm) window.splMergeTrackMeta(id, rm);
                    }catch(e){}
                    promote();
                    var m = window.__splTrackMeta && window.__splTrackMeta[id];
                    if((m && m.album != null) || n >= 3){
                        delete window.__splMetaRuns[id];
                        resolve();
                        return;
                    }
                    setTimeout(function(){ attempt(n + 1, resolve); }, [400, 600, 800][n]);
                };

                var p = new Promise(function(resolve){ attempt(0, resolve); });
                window.__splMetaRuns[id] = p;
                return p;
            };

            window.updMedia = function(){
                var id = (typeof window.splPlayingTrackId === 'function')
                    ? window.splPlayingTrackId()
                    : (window.splTrackId || window.__curTrackId || null);
                var m = (id && window.__splTrackMeta && window.__splTrackMeta[id]) || {};
                var album = m.album || '';

                var restart = (typeof position === 'number' && typeof lastPos === 'number' && position < lastPos - 2000);
                lastPos = position;
                if(restart) lastState = null;

                var currState=track+'|'+artist+'|'+playing+'|'+repmode+'|'+isfav+'|'+shuffle+'|'+album+'|'+duration+'|'+cover;
                if(currState!==lastState) {
                    lastState=currState;
                    var values={artist:artist,track:track,album:album,playing:playing,repeat:repmode,fav:isfav,shuffle:shuffle,duration:duration,position:position,cover:cover};
                    AndBridge.recMediaStatus(JSON.stringify(values));
                } else {
                    AndBridge.recMediaPosition(position);
                }
            };

            window.__splEnsureTrackMetaSafe = function(id){
                try{
                    var p = window.splEnsureTrackMeta(id);
                    if(p && typeof p.catch === 'function'){
                        p.catch(function(e){
                            try{ AndBridge.dbg('e','meta ensure failed: '+(e&&e.message||e)); }catch(_){}
                        });
                    }
                    return p;
                }catch(e){ return null; }
            };

            try {
                if(typeof window.splOnTrackChange === 'function'){
                    window.splOnTrackChange(function(uri, id){
                        if(!id) return;
                        if(window.__curTrackId === id){
                            window.__splEnsureTrackMetaSafe(id);
                            return;
                        }
                        var m = (window.__splTrackMeta && window.__splTrackMeta[id]) || null;
                        window.__curTrackId = id;
                        window.__curTrackUri = uri;
                        window.__curTrackName = m ? m.name : null;
                        window.__curTrackArtist = m ? m.artist : null;
                        window.__curTrackAlbum = m ? m.album : null;
                        window.__curTrackCover = m ? m.cover : null;
                        window.__splEnsureTrackMetaSafe(id);
                        if(typeof track !== 'undefined'){ try{ window.updMedia(); }catch(e){} }
                    });
                }
            } catch(e){}
    """
}