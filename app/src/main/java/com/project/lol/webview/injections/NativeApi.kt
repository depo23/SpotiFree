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

            window.splNativeQueue = async function(){
                var qb = document.querySelector('button[data-testid="control-button-queue"]');
                if (!qb) throw new Error('queue button not found');
                var rows = function(){
                    return Array.prototype.filter.call(document.querySelectorAll('[data-testid="tracklist-row"]'), function(r){
                        return !r.closest('main') && !r.closest('#Desktop_LeftSidebar_Id');
                    });
                };
                var wasOpen = rows().length > 0;
                if (!wasOpen) qb.click();
                var list = [];
                for (var n = 0; n < 20; n++) {
                    await sleep(250);
                    list = rows();
                    if (list.length) { await sleep(300); list = rows(); break; }
                }
                var out = list.map(function(r){
                    var a = r.querySelector('a[data-testid="internal-track-link"]') || r.querySelector('a[href*="/track/"]');
                    var m = a ? /\/track\/([A-Za-z0-9]{22})/.exec(a.getAttribute('href') || '') : null;
                    var arts = Array.prototype.map.call(r.querySelectorAll('a[href*="/artist/"]'), function(x){ return (x.textContent || '').trim(); }).filter(Boolean);
                    var im = r.querySelector('img');
                    return {
                        uri: m ? 'spotify:track:' + m[1] : '', name: a ? (a.textContent || '').trim() : '',
                        artist: arts.filter(function(v, i){ return arts.indexOf(v) === i; }).join(', '),
                        image: im ? im.src : ''
                    };
                }).filter(function(t){ return t.uri; });
                if (!wasOpen && rows().length) qb.click();
                return { items: out };
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
