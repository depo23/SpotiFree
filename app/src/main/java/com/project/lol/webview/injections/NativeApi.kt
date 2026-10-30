package com.project.lol.webview.injections

/*
 * Calls from the native UI layer: search, artist data, play and navigation run
 * inside the page, which already holds the login token, and the JSON answer is
 * sent back through AndBridge.nativeResult.
 */

object NativeApi {
    const val CONTENT = """
        (function () {
            if (window.splNative) return;

            window.splNativeNav = function(path){
                try {
                    history.pushState({}, '', path);
                    window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
                } catch(e){ location.href = path; }
            };

            function sleep(ms){ return new Promise(function(r){ setTimeout(r, ms); }); }

            function src(o){
                var a = o && o.sources;
                return (a && a.length) ? (a[Math.min(1, a.length - 1)].url || a[0].url || '') : '';
            }

            function mapRelease(r){
                if (!r) return null;
                var id = r.id || String(r.uri || '').split(':').pop();
                if (!id) return null;
                return {
                    id: id, uri: r.uri || ('spotify:album:' + id), name: r.name || '',
                    image: src(r.coverArt), year: String((r.date && r.date.year) || ''),
                    type: String(r.type || 'ALBUM').toLowerCase()
                };
            }

            window.splNativeArtist = async function(uri){
                var id = String(uri).split(':').pop();
                window.__splArtistOv = window.__splArtistOv || {};
                var u = window.__splArtistOv[id];
                if (!u) {
                    window.splNativeNav('/artist/' + id);
                    for (var n = 0; n < 50 && !window.__splArtistOv[id]; n++) await sleep(300);
                    u = window.__splArtistOv[id];
                }
                if (!u) throw new Error('the web page did not load the artist');
                var d = u.discography || {};
                var top = ((d.topTracks && d.topTracks.items) || []).map(function(x){
                    var t = x.track || x;
                    var al = t.albumOfTrack || {};
                    return {
                        uri: t.uri, name: t.name, album: al.name || '', image: src(al.coverArt),
                        duration: (t.duration && t.duration.totalMilliseconds) || 0,
                        plays: String(t.playcount || '')
                    };
                }).filter(function(t){ return t.uri; });
                var rel = [];
                var add = function(r){ var m = mapRelease(r); if (m && !rel.some(function(q){ return q.id === m.id; })) rel.push(m); };
                ((d.popularReleasesAlbums && d.popularReleasesAlbums.items) || []).forEach(add);
                ['albums', 'singles', 'compilations'].forEach(function(k){
                    ((d[k] && d[k].items) || []).forEach(function(g){
                        ((g.releases && g.releases.items) || [g]).forEach(add);
                    });
                });
                var vis = u.visuals || {};
                var hdr = (vis.headerImage && vis.headerImage.data) || vis.headerImage || null;
                var errs = [];
                if (!top.length) errs.push('no top tracks (keys: ' + Object.keys(d).join(',') + ')');
                return {
                    name: (u.profile && u.profile.name) || '',
                    image: src(hdr) || src(vis.avatarImage),
                    followers: (u.stats && u.stats.followers) || 0,
                    monthly: (u.stats && u.stats.monthlyListeners) || 0,
                    tracks: top, albums: rel, errors: errs
                };
            };

            var B62 = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
            function gidOf(id){
                var n = 0n;
                for (var k = 0; k < id.length; k++) n = n * 62n + BigInt(B62.indexOf(id[k]));
                return n.toString(16).padStart(32, '0');
            }

            async function trackInfo(id){
                var m = window.__splTrackMeta && window.__splTrackMeta[id];
                if (m && m.name) return { name: m.name, artist: m.artist || '', image: m.cover || '' };
                window.__splInfo = window.__splInfo || {};
                if (window.__splInfo[id]) return window.__splInfo[id];
                try {
                    var f = window.mngFetch || window.oriFetch || window.fetch;
                    var r = await f('https://spclient.wg.spotify.com/metadata/4/track/' + gidOf(id) + '?market=from_token', {
                        headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken }
                    });
                    if (!r.ok) return { name: '', artist: '', image: '' };
                    var j = await r.json();
                    var cg = j.album && j.album.cover_group && j.album.cover_group.image;
                    var res = {
                        name: j.name || '',
                        artist: (j.artist || []).map(function(a){ return a.name; }).join(', '),
                        image: (cg && cg.length) ? 'https://i.scdn.co/image/' + cg[0].file_id : ''
                    };
                    if (res.name) window.__splInfo[id] = res;
                    return res;
                } catch(e){ return { name: '', artist: '', image: '' }; }
            }

            window.splNativeQueue = async function(){
                await window.ensureAuthToken();
                var sm = window.__splSM;
                if (!sm || !sm.tracks) throw new Error('no queue data yet, play or skip a track first');
                var cur = 'spotify:track:' + (window.splTrackId || '');
                var uris = [];
                sm.tracks.forEach(function(t){
                    var u = t && t.metadata && t.metadata.uri;
                    if (u && u.indexOf('spotify:track:') === 0) uris.push(u);
                });
                var ctx = window.__splCtxUri || null;
                (function scan(o, d){
                    if (ctx || !o || d > 6) return;
                    if (typeof o === 'string') { if (/^spotify:(playlist|album):[A-Za-z0-9]{22}$/.test(o)) ctx = o; return; }
                    if (typeof o === 'object') for (var k in o) scan(o[k], d + 1);
                })(sm, 0);
                var qc = window.__splQCtx;
                var qcHit = false;
                if (ctx && qc && qc.ctx === ctx && Date.now() - qc.at < 600000 && qc.uris.indexOf(cur) !== -1) { uris = qc.uris; qcHit = true; }
                if (ctx && !qcHit) {
                    try {
                        var f = window.mngFetch || window.oriFetch || window.fetch;
                        var hdr = { headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken } };
                        var full = [];
                        var url = 'https://spclient.wg.spotify.com/context-resolve/v1/' + ctx;
                        var seen = {};
                        for (var pg = 0; pg < 40 && url && !seen[url]; pg++) {
                            seen[url] = 1;
                            var r = await f(url, hdr);
                            try{ AndBridge.dbg('i', 'queue page ' + pg + ' status ' + r.status + ' ' + url.slice(0, 120)); }catch(_){}
                            if (!r.ok) break;
                            var cj = await r.json();
                            var nx = null;
                            (function walk(o, d){
                                if (!o || d > 8) return;
                                if (Array.isArray(o)) { o.forEach(function(x){ walk(x, d + 1); }); return; }
                                if (typeof o === 'object') {
                                    if (typeof o.uri === 'string' && o.uri.indexOf('spotify:track:') === 0) { if (full.indexOf(o.uri) === -1) full.push(o.uri); return; }
                                    if (typeof o.next_page_url === 'string' && o.next_page_url) nx = o.next_page_url;
                                    for (var k in o) walk(o[k], d + 1);
                                }
                            })(cj.pages || cj, 0);
                            if (pg === 0) { try{ AndBridge.dbg('i', 'queue first page keys ' + Object.keys(cj).join(',') + ' next=' + nx); }catch(_){} }
                            url = nx ? (nx.indexOf('hm://') === 0 ? 'https://spclient.wg.spotify.com/' + nx.slice(5) : nx) : null;
                        }
                        try{ AndBridge.dbg('i', 'queue ctx tracks ' + full.length); }catch(_){}
                        if (full.length > uris.length) uris = full;
                    } catch(e){ try{ AndBridge.dbg('e', 'queue ctx failed ' + (e && e.message || e)); }catch(_){} }
                }
                if (ctx && !qcHit && ctx.indexOf('spotify:playlist:') === 0) {
                    try {
                        var pl = [];
                        for (var off = 0; off < 600; off += 100) {
                            var pr = await (window.mngFetch || window.oriFetch || window.fetch)('https://api-partner.spotify.com/pathfinder/v2/query', {
                                method: 'POST',
                                headers: {
                                    'Authorization': window.spotAuthToken,
                                    'Client-Token': window.spotCliToken,
                                    'Content-Type': 'application/json;charset=UTF-8',
                                    'app-platform': 'WebPlayer',
                                    'spotify-app-version': '1.2.40.0'
                                },
                                body: JSON.stringify({
                                    variables: { uri: ctx, offset: off, limit: 100, enableWatchFeedEntrypoint: false },
                                    operationName: 'fetchPlaylist',
                                    extensions: { persistedQuery: { version: 1, sha256Hash: window.getGqlHash('fetchPlaylist', '346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763') } }
                                })
                            });
                            if (!pr.ok) { try{ AndBridge.dbg('i', 'queue gql playlist status ' + pr.status); }catch(_){} break; }
                            var pj = await pr.json();
                            var po = pj.data && (pj.data.playlistV2 || pj.data.playlist);
                            var its = (po && po.content && po.content.items) || [];
                            its.forEach(function(el){
                                var d = (el.itemV2 && el.itemV2.data) || {};
                                var u = d.uri || '';
                                if (u.indexOf('spotify:track:') === 0 && pl.indexOf(u) === -1) pl.push(u);
                            });
                            if (its.length < 100) break;
                        }
                        try{ AndBridge.dbg('i', 'queue gql playlist tracks ' + pl.length); }catch(_){}
                        if (pl.indexOf(cur) !== -1 && pl.length > uris.length) uris = pl;
                    } catch(e){ try{ AndBridge.dbg('e', 'queue gql playlist failed ' + (e && e.message || e)); }catch(_){} }
                }
                if (ctx && !qcHit && uris.indexOf(cur) !== -1 && uris.length > 8) window.__splQCtx = { ctx: ctx, uris: uris, at: Date.now() };
                function after(list){
                    var k = list.indexOf(cur);
                    return (k >= 0 ? list.slice(k + 1) : list).filter(function(u){ return u !== cur; });
                }
                var rows = [];
                document.querySelectorAll('div[data-testid="tracklist-row"] a[href*="/track/"]').forEach(function(a){
                    var m = (a.getAttribute('href') || '').match(/\/track\/([A-Za-z0-9]{22})/);
                    if (m && rows.indexOf('spotify:track:' + m[1]) === -1) rows.push('spotify:track:' + m[1]);
                });
                var nx = window.__splNext;
                var nxl = (nx && nx.uris && (!ctx || !nx.ctx || nx.ctx === ctx)) ? nx.uris.filter(function(u){ return u !== cur; }) : [];
                var cands = [after(uris), rows.indexOf(cur) !== -1 ? after(rows) : [], nxl];
                try{ AndBridge.dbg('i', 'queue sources ctx/sm ' + cands[0].length + ' dom ' + cands[1].length + ' state ' + cands[2].length); }catch(_){}
                var next = cands.reduce(function(a, b){ return b.length > a.length ? b : a; }, []).slice(0, 30);
                var out = new Array(next.length);
                var idx = 0;
                async function worker(){
                    while (idx < next.length) {
                        var k = idx++;
                        var info = await trackInfo(next[k].split(':').pop());
                        out[k] = { uri: next[k], name: info.name, artist: info.artist, image: info.image };
                    }
                }
                await Promise.all([worker(), worker(), worker(), worker(), worker(), worker(), worker(), worker()]);
                return { items: out.filter(function(t){ return t && t.name; }) };
            };

            window.splNative = function(reqId, op, argStr){
                var arg = null;
                try { arg = JSON.parse(argStr); } catch(e){ arg = argStr; }
                var done = function(v){ try { AndBridge.nativeResult(reqId, JSON.stringify(v)); } catch(e){} };
                var fail = function(e){ done({ error: String((e && e.message) || e) }); };
                try {
                    if (op === 'search') {
                        window.ensureAuthToken().then(function(){ return window.searchMediaItems(arg); }).then(done, fail);
                    } else if (op === 'artist') {
                        window.splNativeArtist(arg).then(done, fail);
                    } else if (op === 'canvas') {
                        var same = window.__splCanvasFor && window.__splCanvasFor === window.splTrackId;
                        done({ url: same ? (window.__splCanvasUrl || '') : '' });
                    } else if (op === 'queue') {
                        window.splNativeQueue().then(done, fail);
                    } else {
                        fail('unknown op');
                    }
                } catch(e){ fail(e); }
            };
        })();
    """
}
