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
                try {
                    var f = window.mngFetch || window.oriFetch || window.fetch;
                    var r = await f('https://spclient.wg.spotify.com/metadata/4/track/' + gidOf(id) + '?market=from_token', {
                        headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken }
                    });
                    if (!r.ok) return { name: '', artist: '', image: '' };
                    var j = await r.json();
                    var cg = j.album && j.album.cover_group && j.album.cover_group.image;
                    return {
                        name: j.name || '',
                        artist: (j.artist || []).map(function(a){ return a.name; }).join(', '),
                        image: (cg && cg.length) ? 'https://i.scdn.co/image/' + cg[0].file_id : ''
                    };
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
                var ci = uris.indexOf(cur);
                var next = (ci >= 0 ? uris.slice(ci + 1) : uris).filter(function(u){ return u !== cur; }).slice(0, 30);
                var out = new Array(next.length);
                var idx = 0;
                async function worker(){
                    while (idx < next.length) {
                        var k = idx++;
                        var info = await trackInfo(next[k].split(':').pop());
                        out[k] = { uri: next[k], name: info.name, artist: info.artist, image: info.image };
                    }
                }
                await Promise.all([worker(), worker(), worker(), worker(), worker()]);
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
